module.exports = [
    {
        entry: ['redux'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/redux",
            filename: "redux.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-redux'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/react-redux/",
            filename: "react-redux.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['redux-router'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/webapp/node_modules/redux-router/",
            filename: "redux-router.js",
            libraryTarget: "umd"
        }
    },
    {
        entry: ['react-dom'],
        output: {
            path: "${project.build.outputDirectory}/META-INF/node_modules/react-dom/",
            filename: "react-dom.js",
            libraryTarget: "umd"
        }
    }
];