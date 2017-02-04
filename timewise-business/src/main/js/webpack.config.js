module.exports = [
    {
        entry: ['redux'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules",
            filename: "redux.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-redux'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules",
            filename: "react-redux.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['redux-router'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/",
            filename: "redux-router.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-dom'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/",
            filename: "react-dom.js",
            libraryTarget: "umd"
        }
    }
];