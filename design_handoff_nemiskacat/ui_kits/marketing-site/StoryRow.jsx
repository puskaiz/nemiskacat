/* global React */
function StoryRow() {
  return (
    <section className="section tone">
      <div className="container">
        <div className="story">
          <div className="story-img">
            <div className="tag">Műhely · Budapest</div>
          </div>
          <div className="story-text">
            <div className="section-eyebrow">Bútorfelújítás tudásanyagok</div>
            <h2>Bútordíszítés házilag. Így újítsd fel a bútoraidat kreatívan.</h2>
            <p>
              Idővel a legszebb asztalka vagy szekrény is megkopik, de az is lehet, hogy
              az ízlésünk változott meg, vagy éppen a lakás berendezését cseréltük le —
              és az adott darab már nem illik a meglévő bútoraink közé.
            </p>
            <p>
              Blogcikkek és részletes tutorial videók bútorfestés, bútorfelújítás és
              lakberendezés témában. Kéthetente új cikkel várunk.
            </p>
            <div>
              <button className="btn btn-secondary">Megyek olvasni, inspirálódni</button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

window.StoryRow = StoryRow;
