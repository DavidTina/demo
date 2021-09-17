var url = location.origin + "asset_url(/util/b.js)";
var script = document.createElement("script");
script.src = url;
document.getElementsByTagName("body")[0].appendChild(script);