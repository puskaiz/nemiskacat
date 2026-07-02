// Header navigation island: mobile hamburger toggle + accessible dropdown menus
// (Rólunk, Tudástár). Desktop opens dropdowns on hover/focus via CSS; this adds
// click/tap + keyboard control and mobile menu open/close. Session-independent.
(function () {
    'use strict';

    // ── Mobile hamburger: toggle the .nav-left panel ──────────────────────────
    var toggle = document.querySelector('.nav-toggle');
    var navLeft = document.getElementById('navMenu');
    if (toggle && navLeft) {
        toggle.addEventListener('click', function () {
            var open = navLeft.classList.toggle('open');
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    }

    // ── Dropdown menus (Rólunk, Tudástár) ─────────────────────────────────────
    var triggers = Array.prototype.slice.call(document.querySelectorAll('.nav-trigger'));

    function closeAll(except) {
        triggers.forEach(function (btn) {
            if (btn === except) return;
            var dd = btn.nextElementSibling;
            if (dd) dd.classList.remove('open');
            btn.setAttribute('aria-expanded', 'false');
        });
    }

    triggers.forEach(function (btn) {
        var dd = btn.nextElementSibling;
        if (!dd) return;
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var open = dd.classList.toggle('open');
            btn.setAttribute('aria-expanded', open ? 'true' : 'false');
            closeAll(btn);
        });
    });

    // Click outside any dropdown closes them.
    document.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('.nav-item.has-dropdown')) return;
        closeAll(null);
    });

    // Escape closes dropdowns (and returns focus to the trigger it came from).
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') closeAll(null);
    });
})();
