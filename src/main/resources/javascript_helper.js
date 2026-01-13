// JavaScript helper functions for Bylins MUD Client

// Глобальные функции для удобства
function send(command) { api.send(command); }
function echo(text) { api.echo(text); }
function log(message) { api.log(message); }
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
