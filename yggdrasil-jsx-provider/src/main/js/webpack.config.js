module.exports = [
    {
        entry: ['babel-standalone'],
        output: {
            path: __dirname + "/../../../src-gen/main/resources/js-server/",
            filename: "babel.js"
        }
    },
    {
        entry: ['react'],
        output: {
            path: __dirname + "/../../../src-gen/main/resources/js-server/",
            filename: "react.js"
        }
    },
    {
        entry: ['react-dom/server'],
        output: {
            path: __dirname + "/../../../src-gen/main/resources/js-server/",
            filename: "react-dom-server.js"
        }
    }
];