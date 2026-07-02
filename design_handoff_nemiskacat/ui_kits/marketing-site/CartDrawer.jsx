/* global React */
function CartDrawer({ open, onClose, items }) {
  const total = items.reduce((s, it) => s + it.priceNum, 0);
  return (
    <React.Fragment>
      <div className={`backdrop ${open ? 'open' : ''}`} onClick={onClose} />
      <aside className={`drawer ${open ? 'open' : ''}`}>
        <div className="drawer-head">
          <h3>Kosarad</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Bezár">✕</button>
        </div>
        <div className="drawer-body">
          {items.length === 0 && (
            <div style={{ color: 'var(--nk-fg-muted)', fontFamily: 'Lora, serif' }}>
              Még üres — válogass a webshopban!
            </div>
          )}
          {items.map((it, i) => (
            <div className="cart-line" key={i}>
              <div className="thumb"><div className="sw" style={{ background: it.color }} /></div>
              <div>
                <div className="name">{it.name}</div>
                <div className="meta">{it.brand}</div>
              </div>
              <div className="price">{it.price}</div>
            </div>
          ))}
        </div>
        <div className="drawer-foot">
          <div className="subtotal">
            <span>Összesen</span>
            <span className="v">{total.toLocaleString('hu-HU')} Ft</span>
          </div>
          <button className="btn btn-primary" style={{ width: '100%' }}>
            <img src="../../assets/icons/delivery.png" alt="" />
            Tovább a fizetéshez
          </button>
        </div>
      </aside>
    </React.Fragment>
  );
}

window.CartDrawer = CartDrawer;
