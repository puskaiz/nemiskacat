/* global React */
function CategoryGrid() {
  const cats = [
    { icon: '../../assets/icons/paint-can.png', name: 'Bútorfestékek',  count: '128 termék' },
    { icon: '../../assets/icons/house.png',     name: 'Falfestékek',     count: '42 termék'  },
    { icon: '../../assets/icons/brush.png',     name: 'Ecsetek & eszközök', count: '64 termék'  },
    { icon: '../../assets/icons/idea.png',      name: 'IOD dekor & szilikon formák', count: '36 termék' },
  ];
  return (
    <section className="section tone">
      <div className="container">
        <div className="section-header">
          <div className="section-eyebrow">Festékek webshop</div>
          <h2 className="section-title">Minden, ami a jó alkotáshoz kell.</h2>
          <p className="section-sub">
            Bútorfestékek, falfestékek, waxok, tartós Polyvine lakkok, Iron Orchid Designs bútordekor,
            Zibra ecsetek — gondosan válogatva, raktárról szállítva.
          </p>
        </div>
        <div className="cat-grid">
          {cats.map(c => (
            <div className="cat-tile" key={c.name}>
              <img src={c.icon} alt="" />
              <div>
                <div className="name">{c.name}</div>
                <div className="count">{c.count}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

window.CategoryGrid = CategoryGrid;
