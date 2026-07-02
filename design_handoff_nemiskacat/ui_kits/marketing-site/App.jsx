/* global React, ReactDOM, Nav, Hero, CategoryGrid, FeaturedProducts, StoryRow, Testimonial, WorkshopCard, Footer, CartDrawer */
const { useState } = React;

function App() {
  const [cartOpen, setCartOpen] = useState(false);
  const [cart, setCart] = useState([
    { brand: 'Annie Sloan · Krétafesték', name: 'Chalk Paint — Country Grey', price: '6 800 Ft', priceNum: 6800, color: '#A3A09B' },
    { brand: 'Polyvine · Lakk',           name: 'Decorator\'s Varnish — Dead Flat', price: '4 400 Ft', priceNum: 4400, color: '#F2EFEA' },
  ]);

  function handleAdd(item) {
    setCart(c => [...c, { ...item, priceNum: parseInt(item.price.replace(/\D/g, ''), 10) }]);
    setCartOpen(true);
  }

  return (
    <React.Fragment>
      <Nav onCartClick={() => setCartOpen(true)} cartCount={cart.length} />
      <Hero />
      <CategoryGrid />
      <FeaturedProducts onAdd={handleAdd} />
      <StoryRow />
      <Testimonial />
      <WorkshopCard />
      <Footer />
      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} items={cart} />
    </React.Fragment>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
