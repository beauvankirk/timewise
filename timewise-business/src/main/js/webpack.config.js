module.exports = [
    {
        entry: ['react'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/react",
            filename: "react.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-dom/server'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/react-dom/",
            filename: "server.js",
            libraryTarget: "umd"
        }
    }
];