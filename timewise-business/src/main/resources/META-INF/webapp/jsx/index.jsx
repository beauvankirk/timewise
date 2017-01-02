import React from 'react';
import ReactDOMServer from 'react-dom/server';
import Head from './head.jsx';
import TopBar from './topBar.jsx';

let body = (<body>
    <div id="content">
        <TopBar/>
    </div>
</body>);

var output = '<html>';
output += ReactDOMServer.renderToStaticMarkup((<Head title="Timewise"/>));
output += ReactDOMServer.renderToString(body);
output += '</html>';

export default output;

