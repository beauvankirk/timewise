module.exports = [
    {
        entry: ['babel-standalone'],
        output: {
            path: "${project.basedir}/src-gen/main/resources/META-INF/js-server/",
            filename: "babel.js"
        }
    },
    {
        entry: ['react'],
        output: {
            path: "${project.basedir}/src-gen/main/resources/META-INF/js-server/",
            filename: "react.js"
        }
    },
    {
        entry: ['react-dom/server'],
        output: {
            path: "${project.basedir}/src-gen/main/resources/META-INF/js-server/",
            filename: "react-dom-server.js"
        }
    }
];