var index = lunr(function () {
    this.field('header', {boost: 10});
    this.field('content');
    this.ref('id');
});

var globalData = [];

function loadSearchIndex(data) {
    globalData = data;
    for (var i in data) {
        index.add({
            id: i,
            header: data[i].header,
            content: data[i].content
        })
    }
}

function searchTemplate(title, link, content) {
    return '<div><h2><a href="' + root + '/' + link + '">' + title + '</a></h2></div>' +
        '<div>' + content.substring(0, 120) + '</div>'
}

$('.search-button').click(function () {
    $('.search-modal').show();
    $('.modal-backdrop').show();
    $('#searchInput').focus();
});

$('#searchInput').on('input', function (e) {
    var val = $('#searchInput').val();
    var content = '';
    if (val.length > 1) {
        var result = index.search(val);
        for (var i in result) {
            var ref = result[i].ref;
            var idx = globalData[ref];
            content += searchTemplate(idx.header, idx.file + '?highlight=' + val, idx.content);
        }
    }
    $('.search-output').html(content);
});

$('.modal').find('.close').click(function () {
    $(this).closest('.modal').hide();
    $('.modal-backdrop').hide();
});

function getQueryParams(qs) {
    qs = qs.split('+').join(' ');

    var params = {},
        tokens,
        re = /[?&]?([^=]+)=([^&]*)/g;

    while (tokens = re.exec(qs)) {
        params[decodeURIComponent(tokens[1])] = decodeURIComponent(tokens[2]);
    }

    return params;
}

$(function () {
    var highlight = getQueryParams(document.location.search).highlight;
    if (highlight) {
        var html = $('.content').html();
        html = html.replace(highlight, '<span class="highlight">' + highlight + '</span>');
        $('.content').html(html);
    }
});