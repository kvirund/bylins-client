#!/usr/bin/perl
# Perl IPC wrapper for Bylins MUD Client
use strict;
use warnings;
use utf8;
use JSON::PP;
use IO::Handle;

binmode(STDIN, ':encoding(UTF-8)');
binmode(STDOUT, ':encoding(UTF-8)');
binmode(STDERR, ':encoding(UTF-8)');

STDOUT->autoflush(1);
STDERR->autoflush(1);

my $json = JSON::PP->new->utf8(0)->allow_nonref;

# Storage for loaded scripts
my %scripts;

# Callback storage
my %trigger_callbacks;
my %timer_callbacks;

# API functions for scripts
package MudAPI;

sub new {
    my ($class) = @_;
    return bless {}, $class;
}

sub send {
    my ($self, $cmd) = @_;
    main::send_request('api_send', { command => $cmd });
}

sub echo {
    my ($self, $text) = @_;
    main::send_request('api_echo', { text => $text });
}

sub log {
    my ($self, $msg) = @_;
    main::send_request('api_log', { message => $msg });
}

sub get_var {
    my ($self, $name) = @_;
    my $resp = main::send_request_sync('api_get_var', { name => $name });
    return $resp->{value};
}

sub set_var {
    my ($self, $name, $value) = @_;
    main::send_request('api_set_var', { name => $name, value => $value });
}

sub add_trigger {
    my ($self, $pattern, $callback) = @_;
    my $resp = main::send_request_sync('api_add_trigger', { pattern => $pattern });
    my $trigger_id = $resp->{trigger_id};
    if ($trigger_id) {
        $trigger_callbacks{$trigger_id} = $callback;
    }
    return $trigger_id;
}

sub add_alias {
    my ($self, $pattern, $replacement) = @_;
    main::send_request('api_add_alias', { pattern => $pattern, replacement => $replacement });
}

sub set_timeout {
    my ($self, $delay, $callback) = @_;
    my $resp = main::send_request_sync('api_set_timeout', { delay => $delay });
    my $timer_id = $resp->{timer_id};
    $timer_callbacks{$timer_id} = $callback if $timer_id;
    return $timer_id;
}

sub set_interval {
    my ($self, $interval, $callback) = @_;
    my $resp = main::send_request_sync('api_set_interval', { interval => $interval });
    my $timer_id = $resp->{timer_id};
    $timer_callbacks{$timer_id} = $callback if $timer_id;
    return $timer_id;
}

sub clear_timer {
    my ($self, $timer_id) = @_;
    delete $timer_callbacks{$timer_id};
    main::send_request('api_clear_timer', { timer_id => $timer_id });
}

# Mapper API methods
sub getVariable {
    my ($self, $name) = @_;
    return $self->get_var($name);
}

sub setVariable {
    my ($self, $name, $value) = @_;
    $self->set_var($name, $value);
}

sub handleMovement {
    my ($self, $direction, $room_name, $exits, $room_id) = @_;
    my $exits_str = ref($exits) eq 'ARRAY' ? join(',', @$exits) : $exits;
    my $resp = main::send_request_sync('api_handle_movement', {
        direction => $direction,
        room_name => $room_name,
        exits => $exits_str,
        room_id => $room_id // ''
    });
    return $resp;
}

sub getCurrentRoom {
    my ($self) = @_;
    my $resp = main::send_request_sync('api_get_current_room', {});
    # Возвращаем nil если комната не найдена
    return undef unless $resp && $resp->{id};
    # Возвращаем объект-обёртку с методом get()
    return bless $resp, 'RoomResult';
}

# Обёртка для результата комнаты
package RoomResult;
sub get { return $_[0]->{$_[1]} }
package MudAPI;

sub createRoom {
    my ($self, $room_id, $room_name) = @_;
    my $resp = main::send_request_sync('api_create_room', {
        room_id => $room_id,
        room_name => $room_name
    });
    return $resp->{success};
}

sub setCurrentRoom {
    my ($self, $room_id) = @_;
    main::send_request('api_set_current_room', { room_id => $room_id });
}

sub searchRooms {
    my ($self, $query) = @_;
    my $resp = main::send_request_sync('api_search_rooms', { query => $query });
    # Возвращаем объект-обёртку с методом size()
    my $count = $resp->{count} // 0;
    return bless { _count => $count }, 'SearchResult';
}

# Обёртка для результата поиска
package SearchResult;
sub size { return $_[0]->{_count} }
package MudAPI;

sub addUnexploredExits {
    my ($self, $room_id, $exits) = @_;
    my $exits_str = ref($exits) eq 'ARRAY' ? join(',', @$exits) : $exits;
    main::send_request('api_add_unexplored_exits', {
        room_id => $room_id,
        exits => $exits_str
    });
}

sub setRoomZone {
    my ($self, $room_id, $zone_id) = @_;
    main::send_request('api_set_room_zone', {
        room_id => $room_id,
        zone_id => $zone_id
    });
}

sub setMapEnabled {
    my ($self, $enabled) = @_;
    main::send_request('api_set_map_enabled', { enabled => $enabled ? 'true' : 'false' });
}

package main;

our $api = MudAPI->new();
my %pending_responses;
my $request_id = 0;

# Global wrapper functions for user scripts
sub send { $api->send(@_) }
sub echo { $api->echo(@_) }
sub log { $api->log(@_) }
sub get_var { $api->get_var(@_) }
sub set_var { $api->set_var(@_) }
sub add_trigger { $api->add_trigger(@_) }
sub add_alias { $api->add_alias(@_) }
sub set_timeout { $api->set_timeout(@_) }
sub set_interval { $api->set_interval(@_) }
sub clear_timer { $api->clear_timer(@_) }

sub send_request {
    my ($type, $data) = @_;
    $data //= {};
    my $msg = $json->encode({ type => $type, data => $data });
    print $msg, "\n";
}

sub send_request_sync {
    my ($type, $data) = @_;
    $data //= {};
    my $id = ++$request_id;
    my $msg = $json->encode({ type => $type, data => $data, id => $id });
    print $msg, "\n";

    while (my $line = <STDIN>) {
        chomp $line;
        my $resp = eval { $json->decode($line) };
        if ($resp && $resp->{response_to} && $resp->{response_to} == $id) {
            return $resp->{data} // {};
        } elsif ($resp && $resp->{type}) {
            handle_command($resp);
        }
    }
    return {};
}

sub handle_command {
    my ($cmd) = @_;
    my $type = $cmd->{type} // '';
    my $data = $cmd->{data} // {};
    my $id = $cmd->{id};

    if ($type eq 'load_script') {
        my $script_id = $data->{script_id};
        my $code = $data->{code};
        eval {
            my $pkg = "Script_$script_id";
            $pkg =~ s/-/_/g;
            my $wrapped = qq{
                package $pkg;
                use strict;
                use warnings;
                use utf8;
                our \$api = \$main::api;
                # Import global functions
                *send = \\&main::send;
                *echo = \\&main::echo;
                *log = \\&main::log;
                *get_var = \\&main::get_var;
                *set_var = \\&main::set_var;
                *add_trigger = \\&main::add_trigger;
                *add_alias = \\&main::add_alias;
                *set_timeout = \\&main::set_timeout;
                *set_interval = \\&main::set_interval;
                *clear_timer = \\&main::clear_timer;
                $code
                1;
            };
            eval $wrapped;
            die $@ if $@;
            $scripts{$script_id} = $pkg;

            my $on_load = $pkg . "::on_load";
            if (defined &{$on_load}) {
                no strict 'refs';
                &{$on_load}($api);
            }
        };
        my $error = $@;
        send_response($id, { success => !$error, error => $error // '' });
    }
    elsif ($type eq 'unload_script') {
        my $script_id = $data->{script_id};
        my $pkg = $scripts{$script_id};
        if ($pkg) {
            my $on_unload = $pkg . "::on_unload";
            if (defined &{$on_unload}) {
                no strict 'refs';
                eval { &{$on_unload}() };
            }
            delete $scripts{$script_id};
        }
        send_response($id, { success => 1 });
    }
    elsif ($type eq 'call_function') {
        my $script_id = $data->{script_id};
        my $func = $data->{function};
        my $args = $data->{args} // [];
        my $pkg = $scripts{$script_id};
        my $result;
        my $error;
        if ($pkg) {
            my $full_func = $pkg . "::" . $func;
            if (defined &{$full_func}) {
                no strict 'refs';
                eval { $result = &{$full_func}(@{$args}) };
                $error = $@;
            }
        }
        send_response($id, { result => $result, error => $error // '' });
    }
    elsif ($type eq 'trigger_fired') {
        my $trigger_id = $data->{trigger_id};
        my $line = $data->{line};
        my $groups_str = $data->{groups} // '';
        # Парсим строку с разделителями \x1F (между парами) и \x1E (между ключ-значение)
        my $groups = {};
        if ($groups_str) {
            for my $pair (split /\x1F/, $groups_str) {
                if ($pair =~ /^(\d+)\x1E(.*)$/s) {
                    $groups->{$1} = $2;
                }
            }
        }
        my $cb = $trigger_callbacks{$trigger_id};
        if ($cb) {
            eval { $cb->($line, $groups) };
            warn "Trigger callback error: $@" if $@;
        } else {
            warn "No callback found for trigger_id: $trigger_id";
        }
    }
    elsif ($type eq 'timer_fired') {
        my $timer_id = $data->{timer_id};
        my $cb = $timer_callbacks{$timer_id};
        if ($cb) {
            eval { $cb->() };
            warn "Timer callback error: $@" if $@;
        }
    }
    elsif ($type eq 'execute') {
        my $code = $data->{code};
        eval {
            eval $code;
            die $@ if $@;
        };
        send_response($id, { success => !$@, error => $@ // '' });
    }
    elsif ($type eq 'ping') {
        send_response($id, { pong => 1 });
    }
    elsif ($type eq 'shutdown') {
        send_response($id, { ok => 1 });
        exit(0);
    }
}

sub send_response {
    my ($id, $data) = @_;
    return unless defined $id;
    my $msg = $json->encode({ response_to => $id, data => $data });
    print $msg, "\n";
}

# Main loop
while (my $line = <STDIN>) {
    chomp $line;
    next unless $line;
    my $cmd = eval { $json->decode($line) };
    if ($@) {
        warn "JSON parse error: $@";
        next;
    }
    handle_command($cmd);
}
