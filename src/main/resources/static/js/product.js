/*
 * Product page: variant size-picker + quantity stepper + add-to-cart, matching
 * the design preview. Uses window.nkCart.add (cart.js) for the server call.
 */
(function () {
    'use strict';

    // ── description tabs (Termékleírás / Videó / Szállítás) ──
    var tabs = Array.prototype.slice.call(document.querySelectorAll('.tabs-nav .tab'));
    var panels = Array.prototype.slice.call(document.querySelectorAll('.tab-panel'));
    tabs.forEach(function (t) {
        t.addEventListener('click', function () {
            var name = t.getAttribute('data-tab');
            tabs.forEach(function (x) { x.classList.toggle('on', x === t); });
            panels.forEach(function (p) { p.classList.toggle('on', p.getAttribute('data-panel') === name); });
        });
    });

    var addBtn = document.getElementById('addToCart');
    if (!addBtn || !window.nkCart) return;

    var defaultLabel = addBtn.innerHTML;

    // ── quantity stepper ──────────────────────────
    var qtyEl = document.querySelector('[data-qty]');
    var qty = 1;
    function setQty(n) { qty = Math.max(1, n); if (qtyEl) qtyEl.textContent = qty; }
    var dec = document.querySelector('[data-qty-dec]');
    var inc = document.querySelector('[data-qty-inc]');
    if (dec) dec.addEventListener('click', function () { setQty(qty - 1); });
    if (inc) inc.addEventListener('click', function () { setQty(qty + 1); });

    // ── option picker: variant sizes (variable) or workshop session rows ──
    var sizes = Array.prototype.slice.call(
        document.querySelectorAll('.sizes .sz, .sessions .session-row'));
    var statusLine = document.querySelector('[data-variant-status]');
    var selectedSku = addBtn.getAttribute('data-sku'); // set directly for simple products

    function select(btn) {
        sizes.forEach(function (b) { b.classList.remove('on'); });
        btn.classList.add('on');
        selectedSku = btn.getAttribute('data-sku');
        if (statusLine) {
            // omit the plain "Készleten"; only selectable (orderable) variants reach
            // here, so any shown state (Utolsó néhány darab, Előrendelhető) is positive
            var st = btn.getAttribute('data-status');
            var show = st && st !== 'Készleten';
            statusLine.textContent = show ? st : '';
            statusLine.className = 'stock-line in';
            statusLine.style.display = show ? '' : 'none';
        }
    }

    sizes.forEach(function (b) {
        if (!b.disabled) b.addEventListener('click', function () { select(b); });
    });

    if (sizes.length) {
        var firstEnabled = sizes.filter(function (b) { return !b.disabled; })[0];
        if (firstEnabled) {
            select(firstEnabled);
        } else {
            addBtn.disabled = true;
            addBtn.textContent = 'Elfogyott';
        }
    }

    // ── add to cart ───────────────────────────────
    addBtn.addEventListener('click', function () {
        if (!selectedSku) return;
        addBtn.disabled = true;
        window.nkCart.add(selectedSku, qty)
            .then(function () {
                addBtn.classList.add('added');
                addBtn.textContent = 'Kosárban ✓';
                setTimeout(function () { addBtn.innerHTML = defaultLabel; addBtn.classList.remove('added'); }, 1800);
            })
            .catch(function () { addBtn.textContent = 'Sikertelen'; })
            .finally(function () { addBtn.disabled = false; });
    });
})();
