#!/usr/bin/perl
# -*- coding: utf-8 -*-
# Автомаппер для Bylins MUD (Perl версия)
#
# Парсит:
#   Название комнаты: "Постоялый двор [5001]"
#   Выходы в промпте: "Вых:СЮЗВv^>" или "Вых:(С)В(Ю)>"
#

use strict;
use warnings;
use utf8;

# Состояние
our $pending_room_id = undef;
our $pending_room_name = undef;
our @pending_exits = ();
our $last_direction = undef;

# Маппинг символов выходов
our %exit_chars = (
    "С" => "north", "с" => "north",
    "Ю" => "south", "ю" => "south",
    "З" => "west",  "з" => "west",
    "В" => "east",  "в" => "east",
    "^" => "up",
    "v" => "down"
);

# Маппинг направлений
our %directions = (
    "север"  => "north",
    "юг"     => "south",
    "запад"  => "west",
    "восток" => "east",
    "вверх"  => "up",
    "вниз"   => "down"
);

sub is_debug {
    my $val = $api->getVariable("automapper_debug");
    return ($val eq "true" || $val eq "1");
}

sub debug {
    my ($msg) = @_;
    if (is_debug()) {
        $api->echo("[Mapper DEBUG] $msg");
    }
}

sub try_create_room {
    my $exits_count = scalar(@pending_exits);
    $api->log("[PL] try_create_room: room_id=" . ($pending_room_id // "nil") .
              " room_name=" . ($pending_room_name // "nil") .
              " exits=$exits_count" .
              " direction=" . ($last_direction // "nil"));

    return unless defined $pending_room_id;
    return unless defined $pending_room_name;
    return unless @pending_exits;

    my $room_id = $pending_room_id;
    my $room_name = $pending_room_name;
    my $direction = $last_direction;

    # Вычисляем зону
    my $zone_id = 0;
    if ($room_id =~ /^\d+$/) {
        $zone_id = int($room_id / 100);
    }

    if (defined $direction) {
        # Движение - создаём связь
        my $exits_str = join(",", @pending_exits);
        $api->log("[PL] handle_movement: dir=$direction exits=$exits_str roomId=$room_id");
        my $room_info = $api->handleMovement($direction, $room_name, \@pending_exits, $room_id);
        if ($room_info) {
            $api->setRoomZone($room_id, "zone_$zone_id");
            $api->echo("[Mapper] $room_name [$room_id]");
        } else {
            debug("handleMovement returned undef");
        }
    } else {
        # Начальная комната
        $api->log("[PL] Initial room case");
        my $current = $api->getCurrentRoom();
        my $current_id = undef;
        if ($current) {
            eval {
                $current_id = $current->get("id");
            };
        }

        $api->log("[PL] current_id=" . ($current_id // "nil") . " room_id=$room_id");

        if (!defined($current_id) || $current_id ne $room_id) {
            my $rooms = $api->searchRooms($room_id);
            my $rooms_count = $rooms ? $rooms->size() : 0;
            $api->log("[PL] search_rooms($room_id) found $rooms_count");

            if ($rooms_count == 0) {
                $api->log("[PL] Creating new room: $room_id");
                if ($api->createRoom($room_id, $room_name)) {
                    $api->setRoomZone($room_id, "zone_$zone_id");
                    $api->setCurrentRoom($room_id);
                    if (@pending_exits) {
                        $api->addUnexploredExits($room_id, \@pending_exits);
                        my $exits_str = join(",", @pending_exits);
                        $api->log("[PL] Added exits: $exits_str");
                    }
                    $api->echo("[Mapper] New: $room_name [$room_id]");
                } else {
                    $api->log("[PL] create_room returned false");
                }
            } else {
                $api->log("[PL] Room exists, setting current");
                $api->setCurrentRoom($room_id);
                if (@pending_exits) {
                    $api->addUnexploredExits($room_id, \@pending_exits);
                    my $exits_str = join(",", @pending_exits);
                    $api->log("[PL] Updated exits: $exits_str");
                }
            }
        } else {
            $api->log("[PL] current_id == room_id, skipping");
        }
    }

    # Сброс
    $pending_room_id = undef;
    $pending_room_name = undef;
    @pending_exits = ();
    $last_direction = undef;
}


# Триггер: название комнаты "Постоялый двор [5001]"
add_trigger('(.+)\[(\d+)\]', sub {
    my ($line, $groups) = @_;

    $api->log("[PL] room callback, groups[1]=" . ($groups->{1} // "") . " groups[2]=" . ($groups->{2} // ""));

    my $name = $groups->{1};
    my $room_id = $groups->{2};

    return unless $name && $room_id;

    # Игнорируем строки карты
    my $trimmed = $name;
    $trimmed =~ s/^\s+|\s+$//g;
    return unless $trimmed;

    my $first = substr($trimmed, 0, 1);
    return if $first =~ /^[:|\ -]$/;

    $pending_room_name = $trimmed;
    $pending_room_id = $room_id;
    try_create_room();
});


# Триггер: выходы "Вых:СЮv^>" или "Вых:(С)В(Ю)>"
add_trigger('Вых:([\(СЮЗВсюзвv^\)]+)>', sub {
    my ($line, $groups) = @_;

    my $exits_str = $groups->{1};
    $api->log("[PL] exits callback, groups[1]=" . ($exits_str // ""));

    return unless $exits_str;

    my @exits = ();
    my %seen = ();
    my $in_brackets = 0;

    # Итерация по UTF-8 символам
    my @chars = split(//, $exits_str);
    for my $char (@chars) {
        if ($char eq "(") {
            $in_brackets = 1;
        } elsif ($char eq ")") {
            $in_brackets = 0;
        } else {
            my $dir = $exit_chars{$char};
            if ($dir && !$seen{$dir}) {
                push @exits, $dir;
                $seen{$dir} = 1;
            }
        }
    }

    debug("exits: " . join(",", @exits));
    @pending_exits = @exits;
    try_create_room();
});


# Триггер: движение "Вы пошли на север"
add_trigger('Вы \S+ .*(север|юг|запад|восток|вверх|вниз)', sub {
    my ($line, $groups) = @_;

    my $dir_text = $groups->{1};
    $api->log("[PL] movement callback, dir=" . ($dir_text // ""));

    if ($dir_text) {
        my $dir = $directions{lc($dir_text)};
        if ($dir) {
            $last_direction = $dir;
            $pending_room_name = undef;
            $pending_room_id = undef;
            @pending_exits = ();
        }
    }
});


# При загрузке
sub on_load {
    unless ($api->getVariable("automapper_debug")) {
        $api->setVariable("automapper_debug", "false");
    }

    $api->setMapEnabled(1);
    $api->echo("[Automapper PL] Script loaded");
    $api->echo("[Automapper PL] Debug: #vars automapper_debug=true/false");
}

on_load();

1;
