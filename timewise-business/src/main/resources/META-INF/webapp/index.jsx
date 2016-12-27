var head = (<html>
    <head>
        <title>Timewise</title>

    </head>
</html>);

ReactDOMServer.renderToStaticMarkup(head);

var MyComponent = React.createClass({
    render: function(){
        return (
            <h1>Hello, world!</h1>
        );
    }
});

var body = (<body>
    <div id="myDiv">
        <MyComponent/>
    </div>
</body>);

ReactDOMServer.renderToString(body);
