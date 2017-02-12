if (typeof window === 'undefined') {
    window = this;
}
(function() {
    var JSPrinter = Java.type('io.squark.yggdrasil.jsx.handler.JSPrinter');
    var System = Java.type('java.lang.System');
    var Level = Java.type('org.slf4j.event.Level');

    var jsPrinter = new JSPrinter();

    //TODO: Pass JS Stack to be able to log file and line number

    var consoleLog = function() {
        var arr = [];
        for (var index = 0; index < arguments.length; index++) {
            arr[index] = (typeof arguments[index] === 'object' ||typeof arguments[index] === 'function') ? JSON.stringify(arguments[index], null, 2) : arguments[index];
        }
        jsPrinter.log(arr, Level.INFO);
    };
    var consoleErr = function () {
        var arr = [];
        for (var index = 0; index < arguments.length; index++) {
            arr[index] = (typeof arguments[index] === 'object' ||typeof arguments[index] === 'function') ? JSON.stringify(arguments[index], null, 2) : arguments[index];
        }
        jsPrinter.log(arr, Level.ERROR);
    };
    var consoleDebug = function () {
        var arr = [];
        for (var index = 0; index < arguments.length; index++) {
            arr[index] = (typeof arguments[index] === 'object' ||typeof arguments[index] === 'function') ? JSON.stringify(arguments[index], null, 2) : arguments[index];
        }
        jsPrinter.log(arr, Level.DEBUG);
    };
    var consoleWarn = function () {
        var arr = [];
        for (var index = 0; index < arguments.length; index++) {
            arr[index] = (typeof arguments[index] === 'object' ||typeof arguments[index] === 'function') ? JSON.stringify(arguments[index], null, 2) : arguments[index];
        }
        jsPrinter.log(arr, Level.WARN);
    };
    var consoleTrace = function () {
        var arr = [];
        for (var index = 0; index < arguments.length; index++) {
            arr[index] = (typeof arguments[index] === 'object' ||typeof arguments[index] === 'function') ? JSON.stringify(arguments[index], null, 2) : arguments[index];
        }
        jsPrinter.log(arr, Level.TRACE);
    };

    var console = {};

    console.debug = consoleDebug;
    console.warn = consoleWarn;
    console.log = consoleLog;
    console.error = consoleErr;
    console.trace = consoleTrace;

    window.console = console;

    window.process = {env: {}};
})();

// http://webreflection.blogspot.com/2014/05/fixing-java-nashorn-proto.html
Object.defineProperty(
    Object.prototype,
    '__proto__',
    {
        enumerable: false,
        configurable: true,
        writeable: true,
        get: function () {
            return Object.getPrototypeOf(this);
        },
        set: function (proto) {
            Object.setPrototypeOf(this, unwrap(proto));
        }
    }
);

// https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/Object/assign#Polyfill
if (!Object.assign) {
    Object.defineProperty(Object, 'assign', {
        enumerable: false,
        configurable: true,
        writable: true,
        value: function (target) {
            'use strict';
            if (target === undefined || target === null) {
                throw new TypeError('Cannot convert first argument to object');
            }

            var to = Object(target);
            for (var i = 1; i < arguments.length; i++) {
                var nextSource = arguments[i];
                if (nextSource === undefined || nextSource === null) {
                    continue;
                }
                nextSource = Object(nextSource);

                var keysArray = Object.keys(nextSource);
                for (var nextIndex = 0, len = keysArray.length; nextIndex < len; nextIndex++) {
                    var nextKey = keysArray[nextIndex];
                    var desc = Object.getOwnPropertyDescriptor(nextSource, nextKey);
                    if (desc !== undefined && desc.enumerable) {
                        to[nextKey] = nextSource[nextKey];
                    }
                }
            }
            return to;
        }
    });
}
