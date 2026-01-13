# -*- coding: utf-8 -*-
# Python helper functions for Bylins MUD Client
from __future__ import unicode_literals

# Глобальные функции для удобства
def send(command):
    api.send(command)

def echo(text):
    api.echo(text)

def mud_log(message):
    api.log(str(message))

# Переменные
def get_var(name):
    return api.getVariable(name)

def set_var(name, value):
    api.setVariable(name, str(value))

# Триггеры - через хелпер с callback'ом
def add_trigger(pattern, callback):
    return _trigger_helper.register(pattern, callback)

# Алиасы
def add_alias(pattern, replacement):
    return api.addAlias(pattern, replacement)

# Таймеры - через хелпер с callback'ом
def set_timeout(callback, delay):
    return _timer_helper.registerTimeout(callback, delay)

def set_interval(callback, interval):
    return _timer_helper.registerInterval(callback, interval)

def clear_timer(timer_id):
    api.clearTimer(timer_id)

# MSDP
def get_msdp(key):
    return api.getMsdpValue(key)

def get_all_msdp():
    return api.getAllMsdpData()

class MsdpHelper:
    def get(self, key):
        return api.getMsdpValue(key)

    def get_all(self):
        return api.getAllMsdpData()

    def get_reportable(self):
        return api.getMsdpReportableVariables()

    def get_reported(self):
        return api.getMsdpReportedVariables()

    def is_enabled(self):
        return api.isMsdpEnabled()

    def report(self, variable_name):
        api.msdpReport(variable_name)

    def unreport(self, variable_name):
        api.msdpUnreport(variable_name)

    def send(self, variable_name):
        api.msdpSend(variable_name)

    def list(self, list_type):
        api.log("msdp.list called with: " + str(list_type))
        api.msdpList(list_type)

    def is_reportable(self, variable_name):
        reportable = self.get_reportable()
        if not reportable:
            return False
        return variable_name in reportable

    def is_reported(self, variable_name):
        reported = self.get_reported()
        if not reported:
            return False
        return variable_name in reported

msdp = MsdpHelper()

# GMCP
def get_gmcp(key):
    return api.getGmcpValue(key)

def get_all_gmcp():
    return api.getAllGmcpData()

# Status panel
class StatusHelper:
    def add_bar(self, id, options=None):
        if options is None:
            options = {}
        label = options.get("label", id)
        value = options.get("value", 0)
        max_val = options.get("max", 100)
        color = options.get("color", "green")
        show_text = options.get("show_text", True)
        order = options.get("order", -1)
        api.statusAddBar(id, label, int(value), int(max_val), color, show_text, int(order))

    def add_text(self, id, options=None):
        if options is None:
            options = {}
        label = options.get("label", id)
        value = options.get("value", "")
        order = options.get("order", -1)
        api.statusAddText(id, label, str(value), int(order))

    def add_flags(self, id, options=None):
        if options is None:
            options = {}
        label = options.get("label", id)
        flags = options.get("flags", [])
        order = options.get("order", -1)
        api.statusAddFlags(id, label, flags, int(order))

    def add_mini_map(self, id, options=None):
        if options is None:
            options = {}
        current_room_id = options.get("current_room_id")
        visible = options.get("visible", True)
        order = options.get("order", -1)
        api.statusAddMiniMap(id, current_room_id, visible, int(order))

    def update(self, id, updates):
        api.statusUpdate(id, updates)

    def remove(self, id):
        api.statusRemove(id)

    def clear(self):
        api.statusClear()

    def get(self, id):
        return api.statusGet(id)

    def exists(self, id):
        return api.statusExists(id)

status = StatusHelper()

# Mapper
class MapperHelper:
    def get_current_room(self):
        return api.getCurrentRoom()

    def get_room(self, room_id):
        return api.getRoom(room_id)

    def search_rooms(self, query):
        return api.searchRooms(query)

    def find_path(self, target_room_id):
        return api.findPath(target_room_id)

    def set_room_note(self, room_id, note):
        api.setRoomNote(room_id, note)

    def set_room_color(self, room_id, color):
        api.setRoomColor(room_id, color)

    def set_room_zone(self, room_id, zone):
        api.setRoomZone(room_id, zone)

    def set_room_tags(self, room_id, tags):
        api.setRoomTags(room_id, tags)

    def create_room(self, id, name):
        return api.createRoom(id, name)

    def create_room_with_exits(self, id, name, exits):
        return api.createRoomWithExits(id, name, exits)

    def link_rooms(self, from_room_id, direction, to_room_id):
        api.linkRooms(from_room_id, direction, to_room_id)

    def add_unexplored_exits(self, room_id, exits):
        api.addUnexploredExits(room_id, exits)

    def handle_movement(self, direction, room_name, exits, room_id=None):
        return api.handleMovement(direction, room_name, exits, room_id)

    def handle_room(self, params):
        api.log("mapper.handle_room called with: " + str(params))
        result = api.handleRoom(params)
        api.log("mapper.handle_room result: " + str(result))
        return result

    def set_enabled(self, enabled):
        api.setMapEnabled(enabled)

    def is_enabled(self):
        return api.isMapEnabled()

    def clear(self):
        api.clearMap()

    def set_current_room(self, room_id):
        api.setCurrentRoom(room_id)

mapper = MapperHelper()
