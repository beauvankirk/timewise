let path = require('path');
let webpack = require('webpack');
let CopyWebpackPlugin = require('copy-webpack-plugin');

module.exports = [
    {
        entry: {
            client: './jsx/client/client.jsx',
            vendors: ["react", "react-dom", "redux", "react-redux"]
        },
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/jsx/client",
            filename: '[name].js'
        },
        plugins: [
            new webpack.optimize.CommonsChunkPlugin({
                name: 'vendors',
                filename: 'vendors.js'
            }),
            new CopyWebpackPlugin([
                {
                    from: path.resolve(__dirname, 'jsx/**/*'),
                    to: "${project.build.outputDirectory}/META-INF/webapp/",
                    ignore: '**/package.json'
                },
                {
                    from: path.resolve(__dirname, 'node_modules/server/node_modules'),
                    to: "${project.build.outputDirectory}/META-INF/webapp/node_modules"
                }
            ])
        ],
        module: {
            rules: [
                {
                    test: /\.jsx$/,
                    loader: 'babel-loader',
                    options: {
                        presets: [
                            ['es2015', {"modules": false}],
                            'react'
                        ]
                    }
                }
            ]
        },
        devtool: 'source-map',
        resolve: {
            extensions: ['.js', 'json', '.jsx', '*'],
            modules: ['node_modules/client/node_modules']
        }
    }
];
