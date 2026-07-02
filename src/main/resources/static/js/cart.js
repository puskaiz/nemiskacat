/*
 * Session island (TERV §3.4) + cart page behaviour. Cacheable pages carry no
 * session data; this fills the cart badge and exposes window.nkCart.add for the
 * product page. CSRF: cookie double-submit — the XSRF-TOKEN cookie (set by
 * /api/session) is echoed in the X-XSRF-TOKEN header on modifying requests.
 */
(function () {
    'use strict';

    function cookie(name) {
        var m = document.cookie.match('(?:^|; )' + name + '=([^;]*)');
        return m ? decodeURIComponent(m[1]) : null;
    }

    function csrfHeaders() {
        var token = cookie('XSRF-TOKEN');
        var h = {'Content-Type': 'application/json'};
        if (token) h['X-XSRF-TOKEN'] = token;
        return h;
    }

    /** Ensures the XSRF-TOKEN cookie exists (the session call sets it). */
    function ensureSession() {
        return fetch('/api/session', {credentials: 'same-origin'})
            .then(function (r) { return r.json(); });
    }

    function updateBadge(count) {
        document.querySelectorAll('[data-cart-count]').forEach(function (el) {
            el.textContent = count > 0 ? String(count) : '';
        });
    }

    /** Public helper: add a variant to the cart, update the badge, resolve to the cart view. */
    function add(sku, quantity) {
        return ensureSession()
            .then(function () {
                return fetch('/api/cart/items', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: csrfHeaders(),
                    body: JSON.stringify({sku: sku, quantity: quantity || 1})
                });
            })
            .then(function (r) {
                if (!r.ok) throw new Error('add failed: ' + r.status);
                return r.json();
            })
            .then(function (view) {
                updateBadge(view.count);
                return view;
            });
    }

    // --- cart drawer hydration ---

    var drawer = document.querySelector('[data-cart-drawer]');
    var backdrop = document.querySelector('[data-cart-backdrop]');

    function buildLineRow(item) {
        var row = document.createElement('div');
        row.className = 'cart-line';

        var thumbDiv = document.createElement('div');
        thumbDiv.className = 'cart-line-thumb';
        if (item.imageUrl) {
            var img = document.createElement('img');
            img.src = item.imageUrl;
            img.alt = item.name || '';
            img.width = 64;
            img.height = 64;
            thumbDiv.appendChild(img);
        }

        var infoDiv = document.createElement('div');
        infoDiv.className = 'cart-line-info';

        var nameEl = document.createElement('p');
        nameEl.className = 'cart-line-name';
        nameEl.textContent = item.name || '';

        var variantEl = document.createElement('p');
        variantEl.className = 'cart-line-variant';
        variantEl.textContent = item.variantLabel || '';

        infoDiv.appendChild(nameEl);
        if (item.variantLabel) infoDiv.appendChild(variantEl);

        var priceEl = document.createElement('span');
        priceEl.className = 'cart-line-price';
        priceEl.textContent = item.lineTotalFormatted || '';

        row.appendChild(thumbDiv);
        row.appendChild(infoDiv);
        row.appendChild(priceEl);
        return row;
    }

    function renderDrawer(view) {
        var linesEl = document.querySelector('[data-cart-lines]');
        var subtotalEl = document.querySelector('[data-cart-subtotal]');
        if (!linesEl) return;

        linesEl.innerHTML = '';
        if (!view.items || view.items.length === 0) {
            var empty = document.createElement('div');
            empty.className = 'cart-drawer-empty';
            empty.textContent = 'A kosár üres.';
            linesEl.appendChild(empty);
        } else {
            view.items.forEach(function (item) {
                linesEl.appendChild(buildLineRow(item));
            });
        }
        if (subtotalEl) subtotalEl.textContent = view.totalFormatted || '';
    }

    function closeDrawer() {
        if (!drawer) return;
        drawer.classList.remove('open');
        if (backdrop) backdrop.classList.remove('open');
        // Re-hide after transition so they are removed from stacking context
        var duration = 380;
        setTimeout(function () {
            if (!drawer.classList.contains('open')) drawer.setAttribute('hidden', '');
            if (backdrop && !backdrop.classList.contains('open')) backdrop.setAttribute('hidden', '');
        }, duration);
    }

    function openDrawer() {
        if (!drawer) return;
        drawer.removeAttribute('hidden');
        if (backdrop) backdrop.removeAttribute('hidden');
        // Trigger reflow so transition fires
        drawer.getBoundingClientRect();
        if (backdrop) backdrop.getBoundingClientRect();
        return ensureSession()
            .then(function () {
                return fetch('/api/cart', {credentials: 'same-origin'});
            })
            .then(function (r) {
                if (!r.ok) throw new Error('cart fetch failed: ' + r.status);
                return r.json();
            })
            .then(function (view) {
                renderDrawer(view);
                updateBadge(view.count);
                drawer.classList.add('open');
                if (backdrop) backdrop.classList.add('open');
            })
            .catch(function () {
                drawer.classList.add('open');
                if (backdrop) backdrop.classList.add('open');
            });
    }

    if (drawer) {
        var closeBtn = drawer.querySelector('[data-cart-close]');
        if (closeBtn) closeBtn.addEventListener('click', closeDrawer);
    }
    if (backdrop) backdrop.addEventListener('click', closeDrawer);

    // Wire cart icon click to open drawer instead of navigating to /kosar
    var cartChip = document.querySelector('.chip.cart');
    if (cartChip) {
        cartChip.addEventListener('click', function (e) {
            e.preventDefault();
            openDrawer();
        });
    }

    window.nkCart = {add: add, ensureSession: ensureSession, csrfHeaders: csrfHeaders, updateBadge: updateBadge, openDrawer: openDrawer};

    // --- mobile nav: hamburger toggles the links dropdown ---
    var navToggle = document.querySelector('.nav-toggle');
    var navMenu = document.getElementById('navMenu');
    if (navToggle && navMenu) {
        navToggle.addEventListener('click', function () {
            var open = navMenu.classList.toggle('open');
            navToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
    }

    // --- cart badge: always reflect the server cart on every page load.
    // The nk_cart cookie is HttpOnly (not readable here) but IS sent with the
    // request, so /api/session returns the real count; without a cart it cheaply
    // returns 0 (no DB hit). ---
    if (document.querySelector('[data-cart-count]')) {
        ensureSession().then(function (s) { updateBadge(s.cartCount); });
    }

    // --- simple data-add-to-cart buttons (qty 1), if any ---
    document.querySelectorAll('[data-add-to-cart]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            btn.disabled = true;
            add(btn.getAttribute('data-add-to-cart'), 1)
                .then(function () {
                    btn.classList.add('added');
                    btn.textContent = 'Kosárban';
                    openDrawer();
                })
                .catch(function () { btn.textContent = 'Sikertelen'; })
                .finally(function () { btn.disabled = false; });
        });
    });

    // --- cart page: quantity change + remove ---
    function refresh() { window.location.reload(); }

    document.querySelectorAll('.cart-grid .qinput').forEach(function (input) {
        input.addEventListener('change', function () {
            var qty = parseInt(input.value, 10);
            if (isNaN(qty) || qty < 0) return;
            fetch('/api/cart/items/' + input.getAttribute('data-item-id'), {
                method: 'PATCH', credentials: 'same-origin', headers: csrfHeaders(),
                body: JSON.stringify({quantity: qty})
            }).then(refresh);
        });
    });

    document.querySelectorAll('.cart-grid .remove').forEach(function (btn) {
        btn.addEventListener('click', function () {
            fetch('/api/cart/items/' + btn.getAttribute('data-item-id'), {
                method: 'DELETE', credentials: 'same-origin', headers: csrfHeaders()
            }).then(refresh);
        });
    });
})();
