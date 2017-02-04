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
            filename: "webpack-wrapper.js"
        },
        module: {
            loaders: [
                {
                    test: /\.json$/,
                    loader: "json-loader"
                }
            ]
        }
    }
];