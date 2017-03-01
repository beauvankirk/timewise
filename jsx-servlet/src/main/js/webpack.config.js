var path = require('path');
var ContextReplacementPlugin = require('webpack/lib/ContextReplacementPlugin');
var webpack = require('webpack');
var ProgressPlugin = require('webpack/lib/ProgressPlugin');

module.exports = [
    {
        entry: ['babel-standalone'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/node_modules",
            filename: "babel.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "react.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-dom/server'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/react-dom/",
            filename: "server.js",
            libraryTarget: "umd"
        }
    },
    {
        devtool: 'source-map',
        entry: ['./webpack-wrapper'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "webpack-wrapper.js",
            pathinfo: true
        },
        externals: {
            'module': 'MODULE_SHIM',
            'fsevents': 'fsevents',
            // 'fs': 'javaFS'
        },
        module: {
            rules: [
                {
                    test: /(webpack|enhanced-resolve).*\.js$/,
                    loader: 'babel-loader',
                    options: {
                        presets: [
                            ['es2015', { "modules": false}],
                        ]
                    }
                }
            ]
        },
        resolve: {
          alias: {
              'fs': path.resolve(__dirname, 'memory-fs-fix')
          }
        },
        plugins: [
            new ContextReplacementPlugin(/.*transformation\/file\/options$/, path.resolve(__dirname, 'node_modules/'), function(fs, callback) {
                callback(null, {
                    'babel-preset-es2015': './babel-preset-es2015',
                    'babel-preset-react': './babel-preset-react'
                });
            }),
            new webpack.optimize.UglifyJsPlugin({
                mangle: false,
                beautify: true
            })
        ],
        node: {
            fsevents: 'empty',
            net: 'empty',
        }
    }
];
