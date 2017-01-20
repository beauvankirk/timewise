var global = this, window = this;
var console = {};
console.debug = print;
console.warn = print;
console.log = print;
console.error = print;
console.trace = print;

(function() {
   var realCreate = Object.create;
   Object.create = function(p, v) { return realCreate(unwrap(p), v); };
   //var realDefineProperty = Object.defineProperty;
   //Object.defineProperty = function(obj, prop, value) { return realDefineProperty(unwrap(obj), prop, value); };
   var realSetPrototypeOf = Object.setPrototypeOf;
   Object.setPrototypeOf = function(subClass, superClass) { return realSetPrototypeOf(subClass, unwrap(superClass)); };
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
})();

// https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/Object/assign#Polyfill
if (!Object.assign) {
    Object.defineProperty(Object, 'assign', {
        enumerable: false,
        configurable: true,
        writable: true,
        value: function(target) {
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
