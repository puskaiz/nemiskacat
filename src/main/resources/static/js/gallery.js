/*
 * Product gallery: thumbnail + prev/next switching of the main image.
 * Pure client-side, no session — keeps the product page cacheable.
 */
(function () {
    'use strict';
    var gallery = document.querySelector('.gallery');
    if (!gallery) return;

    var main = gallery.querySelector('.imgw img');
    var thumbs = Array.prototype.slice.call(gallery.querySelectorAll('.thumbs button'));
    var counter = gallery.querySelector('.counter');
    var prev = gallery.querySelector('.arrow.prev');
    var next = gallery.querySelector('.arrow.next');
    if (!main || thumbs.length === 0) return;

    // each thumb carries its full image url (same asset; CDN resize comes later)
    var sources = thumbs.map(function (b) {
        var img = b.querySelector('img');
        return (img && (img.getAttribute('data-full') || img.src)) || '';
    });
    var index = 0;

    function show(i) {
        index = (i + sources.length) % sources.length;
        main.src = sources[index];
        thumbs.forEach(function (b, n) { b.classList.toggle('on', n === index); });
        if (counter) counter.textContent = (index + 1) + ' / ' + sources.length;
    }

    thumbs.forEach(function (b, n) {
        b.addEventListener('click', function () { show(n); });
    });
    if (prev) prev.addEventListener('click', function () { show(index - 1); });
    if (next) next.addEventListener('click', function () { show(index + 1); });

    var single = sources.length <= 1;
    [prev, next, counter].forEach(function (el) { if (el && single) el.style.display = 'none'; });

    show(0);
})();
