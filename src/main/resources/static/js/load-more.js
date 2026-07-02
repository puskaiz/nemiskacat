/*
 * Load-more island for the blog list.
 *
 * Progressive enhancement: the server renders a condensed numbered pager
 * (the no-JS fallback) plus a hidden "Több betöltése…" button. When JS runs
 * we reveal the button and hide the pager. Each click fetches the next page
 * (the same cacheable, session-independent HTML as a normal navigation),
 * appends its cards, and re-points the button at the page after that by
 * reading the fetched page's own button — so there is no URL math here.
 * When the fetched page carries no button, we're on the last page and the
 * button is removed.
 */
(function () {
  "use strict";

  function init() {
    var buttons = document.querySelectorAll(".nk-loadmore");
    if (!buttons.length) return;

    buttons.forEach(function (btn) {
      btn.hidden = false;
      btn.addEventListener("click", function () {
        load(btn);
      });
    });
    document.querySelectorAll("[data-loadmore-fallback]").forEach(function (el) {
      el.hidden = true;
    });
  }

  function load(btn) {
    if (btn.dataset.loading) return;
    var url = btn.dataset.next;
    if (!url) return;

    var label = btn.textContent;
    btn.dataset.loading = "1";
    btn.disabled = true;
    btn.textContent = "Betöltés…";

    fetch(url, { headers: { "X-Requested-With": "fetch" } })
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.text();
      })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, "text/html");
        var target = document.querySelector(btn.dataset.target);
        if (!target) throw new Error("target not found");

        var newItems = doc.querySelectorAll(btn.dataset.target + " " + btn.dataset.item);
        var firstNew = null;
        newItems.forEach(function (node) {
          var imported = document.importNode(node, true);
          if (!firstNew) firstNew = imported;
          target.appendChild(imported);
        });

        var nextBtn = doc.querySelector(".nk-loadmore");
        if (nextBtn && nextBtn.dataset.next) {
          btn.dataset.next = nextBtn.dataset.next;
          btn.disabled = false;
          btn.textContent = label;
          delete btn.dataset.loading;
        } else {
          btn.remove();
        }

        // Move keyboard focus to the first newly loaded card for a11y.
        // Prefer the visible title link; skip the aria-hidden image link.
        if (firstNew) {
          var link =
            firstNew.querySelector(".nk-blog-card__title-link") ||
            firstNew.querySelector('a:not([aria-hidden="true"])');
          if (link) {
            link.setAttribute("tabindex", "-1");
            link.focus();
          }
        }
      })
      .catch(function () {
        btn.disabled = false;
        btn.textContent = label;
        delete btn.dataset.loading;
      });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
