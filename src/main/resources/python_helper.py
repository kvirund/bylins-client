# -*- coding: utf-8 -*-
# Python helper functions for Bylins MUD Client

# Глобальные функции для удобства
def send(command):
    api.send(command)

def echo(text):
    api.echo(text)

def log(message):
    api.log(message)

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
