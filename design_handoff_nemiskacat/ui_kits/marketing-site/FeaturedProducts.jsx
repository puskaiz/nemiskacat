/* global React */
function FeaturedProducts({ onAdd }) {
  const items = [
    { brand: 'Annie Sloan · Krétafesték',  name: 'Chalk Paint — Country Grey', price: '6 800 Ft', color: '#A3A09B', isNew: false },
    { brand: 'Annie Sloan · Krétafesték',  name: 'Chalk Paint — Arles',        price: '6 800 Ft', color: '#E0B240', isNew: true  },
    { brand: 'Fusion · Ásványfesték',      name: 'Mineral Paint — Algonquin',  price: '5 200 Ft', color: '#3F4F5A', isNew: false },
    { brand: 'Polyvine · Lakk',            name: 'Decorator\'s Varnish — Dead Flat', price: '4 400 Ft', color: '#F2EFEA', isNew: false },
  ];
  return (
    <section className="section">
      <div className="container">
        <div className="section-header">
          <div className="section-eyebrow">Kiemelt termékek</div>
          <h2 className="section-title">A bátrabb otthonok kedvencei.</h2>
        </div>
        <div className="prod-grid">
          {items.map((it, i) => (
            <div className="product" key={i} onClick={() => onAdd && onAdd(it)}>
              <div className="img">
                <div className="swatch" style={{ background: it.color }} />
                {it.isNew && (
                  <div className="new">
                    <img src="../../assets/brushes/brush-circle-yellow.png" alt="" />
                    <div className="t">Új</div>
                  </div>
                )}
              </div>
              <div className="body">
                <div className="brand">{it.brand}</div>
                <div className="name">{it.name}</div>
                <div className="price">{it.price}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

window.FeaturedProducts = FeaturedProducts;
