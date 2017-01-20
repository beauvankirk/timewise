module.exports = [
    {
        entry: ['babel-standalone'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/js-server/",
            filename: "babel.js"
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
    }
];