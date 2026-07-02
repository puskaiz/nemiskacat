/* global React */
function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-inner">
          <div className="col-brand">
            <img src="../../assets/logos/nemiskacat-logo-black.png" alt="nemiskacat" />
            <p>
              Hivatalos Annie Sloan disztribútor Magyarországon. Bútorfestékek,
              falfestékek, kellékek és tudás — egy helyen, kézzel válogatva.
            </p>
          </div>
          <div>
            <h4>Webshop</h4>
            <ul>
              <li><a href="#">Bútorfestékek</a></li>
              <li><a href="#">Falfestékek</a></li>
              <li><a href="#">Ecsetek, eszközök</a></li>
              <li><a href="#">IOD dekor</a></li>
              <li><a href="#">Ajándékutalvány</a></li>
            </ul>
          </div>
          <div>
            <h4>Tudásanyagok</h4>
            <ul>
              <li><a href="#">Blog</a></li>
              <li><a href="#">Workshopok</a></li>
              <li><a href="#">Tutorial videók</a></li>
              <li><a href="#">Színkártya</a></li>
            </ul>
          </div>
          <div>
            <h4>Elérhetőség</h4>
            <ul>
              <li className="contact-row"><img src="../../assets/icons/phone.png" alt="" /><a href="#">+36 20 269 9113</a></li>
              <li className="contact-row"><img src="../../assets/icons/email.png" alt="" /><a href="#">nemiskacat@nemiskacat.hu</a></li>
              <li className="contact-row"><img src="../../assets/icons/house.png" alt="" /><a href="#">Budapest, műhely</a></li>
              <li className="contact-row"><img src="../../assets/icons/delivery.png" alt="" /><a href="#">Szállítás 1–3 nap</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-bottom">
          <span>© 2026 nemiskacat. Minden jog fenntartva.</span>
          <span>Készült Magyarországon.</span>
        </div>
      </div>
    </footer>
  );
}

window.Footer = Footer;
