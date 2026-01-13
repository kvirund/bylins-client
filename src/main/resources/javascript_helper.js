// JavaScript helper functions for Bylins MUD Client

// Глобальные функции для удобства
function send(command) { api.send(command); }
function echo(text) { api.echo(text); }
function mud_log(message) {
    api.log(message);
}
function print(message) { api.print(message); }

// Переменные
function getVar(name) { return api.getVariable(name); }
function setVar(name, value) { api.setVariable(name, value); }

// Триггеры - через хелпер
function addTrigger(pattern, callback) {
    return _triggerHelper.register(pattern, callback);
}

// Алиасы
function addAlias(pattern, replacement) {
    return api.addAlias(pattern, replacement);
}

// Таймеры - через хелпер
function setTimeout(callback, delay) {
    return _timerHelper.registerTimeout(callback, delay);
}

function setInterval(callback, interval) {
    return _timerHelper.registerInterval(callback, interval);
}

function clearTimer(id) {
    api.clearTimer(id);
}

// MSDP
function getMsdp(key) { return api.getMsdpValue(key); }
function getAllMsdp() { return api.getAllMsdpData(); }

var msdp = {
    get: function(key) { return api.getMsdpValue(key); },
    getAll: function() { return api.getAllMsdpData(); },
    getReportable: function() { return api.getMsdpReportableVariables(); },
    getReported: function() { return api.getMsdpReportedVariables(); },
    isEnabled: function() { return api.isMsdpEnabled(); },
    report: function(variableName) { api.msdpReport(variableName); },
    unreport: function(variableName) { api.msdpUnreport(variableName); },
    send: function(variableName) { api.msdpSend(variableName); },
    list: function(listType) {
        api.log("msdp.list called with: " + listType);
        api.msdpList(listType);
    },

    // Проверяет, есть ли переменная в reportable списке
    isReportable: function(variableName) {
        var reportable = this.getReportable();
        if (!reportable) return false;
        for (var i = 0; i < reportable.length; i++) {
            if (reportable[i] === variableName) return true;
        }
        return false;
    },

    // Проверяет, включён ли REPORT для переменной
    isReported: function(variableName) {
        var reported = this.getReported();
        if (!reported) return false;
        for (var i = 0; i < reported.length; i++) {
            if (reported[i] === variableName) return true;
        }
        return false;
    }
};

// GMCP
function getGmcp(key) { return api.getGmcpValue(key); }
function getAllGmcp() { return api.getAllGmcpData(); }

// Статус-панель
var status = {
    addBar: function(id, options) {
        var label = options.label || id;
        var value = options.value || 0;
        var max = options.max || 100;
        var color = options.color || "green";
        var showText = options.showText !== false;
        var order = options.order || -1;
        api.statusAddBar(id, label, value, max, color, showText, order);
    },
    addText: function(id, options) {
        var label = options.label || id;
        var value = options.value || "";
        var order = options.order || -1;
        api.statusAddText(id, label, value, order);
    },
    addFlags: function(id, options) {
        var label = options.label || id;
        var flags = options.flags || [];
        var order = options.order || -1;
        api.statusAddFlags(id, label, flags, order);
    },
    addMiniMap: function(id, options) {
        options = options || {};
        var currentRoomId = options.currentRoomId || null;
        var visible = options.visible !== false;
        var order = options.order || -1;
        api.statusAddMiniMap(id, currentRoomId, visible, order);
    },
    update: function(id, updates) {
        api.statusUpdate(id, updates);
    },
    remove: function(id) {
        api.statusRemove(id);
    },
    clear: function() {
        api.statusClear();
    },
    get: function(id) {
        return api.statusGet(id);
    },
    exists: function(id) {
        return api.statusExists(id);
    }
};

// Маппер
var mapper = {
    getCurrentRoom: function() { return api.getCurrentRoom(); },
    getRoom: function(roomId) { return api.getRoom(roomId); },
    searchRooms: function(query) { return api.searchRooms(query); },
    findPath: function(targetRoomId) { return api.findPath(targetRoomId); },
    setRoomNote: function(roomId, note) { api.setRoomNote(roomId, note); },
    setRoomColor: function(roomId, color) { api.setRoomColor(roomId, color); },
    setRoomZone: function(roomId, zone) { api.setRoomZone(roomId, zone); },
    setRoomTags: function(roomId, tags) { api.setRoomTags(roomId, tags); },
    createRoom: function(id, name) { return api.createRoom(id, name); },
    createRoomWithExits: function(id, name, exits) { return api.createRoomWithExits(id, name, exits); },
    linkRooms: function(fromRoomId, direction, toRoomId) { api.linkRooms(fromRoomId, direction, toRoomId); },
    addUnexploredExits: function(roomId, exits) { api.addUnexploredExits(roomId, exits); },
    handleMovement: function(direction, roomName, exits, roomId) {
        return api.handleMovement(direction, roomName, exits, roomId || null);
    },
    // Высокоуровневая функция для обработки MSDP room данных
    handleRoom: function(params) {
        api.log("mapper.handleRoom called with: " + JSON.stringify(params));
        var result = api.handleRoom(params);
        api.log("mapper.handleRoom result: " + result);
        return result;
    },
    setEnabled: function(enabled) { api.setMapEnabled(enabled); },
    isEnabled: function() { return api.isMapEnabled(); },
    clear: function() { api.clearMap(); },
    setCurrentRoom: function(roomId) { api.setCurrentRoom(roomId); }
};
