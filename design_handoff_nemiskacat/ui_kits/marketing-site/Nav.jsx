/* global React */
const { useState } = React;

function Nav({ onCartClick, cartCount = 2 }) {
  return (
    <nav className="nav">
      <div className="nav-inner">
        <a href="#" className="nav-logo">
          <img src="../../assets/logos/nemiskacat-logo-black.png" alt="nemiskacat" />
        </a>
        <div className="nav-links">
          <a href="#">Webshop</a>
          <a href="#">Bútorfestés</a>
          <a href="#">Workshopok</a>
          <a href="#">Inspiráció</a>
          <a href="#">Rólunk</a>
        </div>
        <div className="nav-actions">
          <button className="icon-btn" aria-label="Keresés">
            <img src="../../assets/icons/eye.png" alt="" />
          </button>
          <button className="icon-btn cart-dot" aria-label="Kosár" onClick={onCartClick}>
            <img src="../../assets/icons/cart.png" alt="" />
          </button>
        </div>
      </div>
    </nav>
  );
}

window.Nav = Nav;
