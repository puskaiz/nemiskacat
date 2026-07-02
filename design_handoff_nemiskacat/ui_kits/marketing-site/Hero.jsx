/* global React */
function Hero() {
  return (
    <section className="container">
      <div className="hero">
        <div className="hero-left">
          <div className="hero-eyebrow">Nemiskacat · Bútorfestés egyszerűen</div>
          <div className="hero-brush">
            <img src="../../assets/brushes/brush-short-yellow.png" alt="" />
            <div className="text">Az ecsetet Te fogod,<br/>a többit mi adjuk!</div>
          </div>
          <p className="hero-sub">
            Annie Sloan krétafestékek, Fusion ásványfestékek, Polyvine lakkok,
            IOD bútordekor — minden, ami egy bátrabb otthonhoz kell.
          </p>
          <div className="hero-cta-row">
            <button className="btn btn-primary">
              <img src="../../assets/icons/brush.png" alt="" />
              Megyek a webshopba
            </button>
            <button className="btn btn-link">Inspirálódom →</button>
          </div>
        </div>
        <div className="hero-right">
          <div className="photo-placeholder" />
          <div className="badge">
            <img src="../../assets/brushes/brush-circle-yellow.png" alt="" />
            <div className="t">Újdonság!</div>
          </div>
          <div className="photo-tag">Annie Sloan · őszi paletta</div>
        </div>
      </div>
    </section>
  );
}

window.Hero = Hero;
