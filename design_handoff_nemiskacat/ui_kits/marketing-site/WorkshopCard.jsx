/* global React */
function WorkshopCard() {
  return (
    <section className="section">
      <div className="container">
        <div className="section-header">
          <div className="section-eyebrow">Workshopok</div>
          <h2 className="section-title">Tanulj velünk — egy nap, egy bútor, sok nevetés.</h2>
        </div>
        <div className="workshop">
          <div className="img" />
          <div className="body">
            <h3>Ingyenes Karácsonyi Alkotónap a műhelyben</h3>
            <p>
              November 22-én reggeltől délutánig nyitva tartunk — gyere el, próbáld ki
              a krétafestéket, készíts saját díszt egy gyertyatartóra vagy üvegre.
              A nyersanyagokat mi adjuk, a kreativitást Te hozod.
            </p>
            <div className="meta">
              <div className="item">
                <img src="../../assets/icons/house.png" alt="" />
                <span>Budapest, Műhely</span>
              </div>
              <div className="item">
                <img src="../../assets/icons/workshop.png" alt="" />
                <span>Max. 12 fő</span>
              </div>
              <div className="item">
                <img src="../../assets/icons/idea.png" alt="" />
                <span>Anyagok mindenkinek</span>
              </div>
            </div>
            <div style={{ marginTop: 16 }}>
              <button className="btn btn-primary">Regisztrálj</button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

window.WorkshopCard = WorkshopCard;
