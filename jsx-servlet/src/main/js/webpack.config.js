var path = require('path');
var ContextReplacementPlugin = require('webpack/lib/ContextReplacementPlugin');

module.exports = [
    {
        entry: ['babel-standalone'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "babel.js"
        },
        module: {
            loaders: [
                {
                    test: /\.json$/,
                    loader: "json-loader"
                }
            ]
        }
    },
    {
        entry: ['react'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "react.js",
            libraryTarget: "umd"
        },
        module: {
            loaders: [
                {
                    test: /\.json$/,
                    loader: "json-loader"
                }
            ]
        }
    },
    {
        entry: ['react-dom/server'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/react-dom/",
            filename: "server.js",
            libraryTarget: "umd"
        },
        module: {
            loaders: [
                {
                    test: /\.json$/,
                    loader: "json-loader"
                }
            ]
        }
    },
    {
        entry: ['./webpack-wrapper'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "webpack-wrapper.js",
            pathinfo: true
        },
        module: {
            loaders: [
                {
                    test: /\.json$/,
                    loader: "json-loader"
                }
            ]
        },
        externals: {
            'module': 'MODULE_SHIM',
            'fsevents': 'fsevents',
            'fs': 'javaFS'
        },
        plugins: [
            new ContextReplacementPlugin(/.*transformation\/file\/options$/, path.resolve(__dirname, 'node_modules/'), function(fs, callback) {
                callback(null, {
                    'babel-preset-es2015': './babel-preset-es2015',
                    'babel-preset-react': './babel-preset-react'
                });
            })
        ],
        node: {
            fsevents: 'empty'
        }
    }
];
