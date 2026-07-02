import export_pages as ep


def test_walk_assembles_heading_and_text_in_order():
    data = [{"elements": [
        {"widgetType": "heading", "settings": {"title": "Bevezető"}},
        {"widgetType": "text-editor", "settings": {"editor": "<p>Szia</p>"}},
    ]}]
    parts = []
    ep.walk(data, "Rólunk", parts)
    assert parts == ["<h2>Bevezető</h2>", "<p>Szia</p>"]


def test_walk_skips_heading_equal_to_page_title():
    data = [{"widgetType": "heading", "settings": {"title": "Rólunk"}}]
    parts = []
    ep.walk(data, "Rólunk", parts)
    assert parts == []


def test_walk_toggle_becomes_h3_plus_content():
    data = [{"widgetType": "toggle", "settings": {"tabs": [
        {"tab_title": "Kérdés?", "tab_content": "<p>Válasz</p>"}]}}]
    parts = []
    ep.walk(data, "GYIK", parts)
    assert parts == ["<h3>Kérdés?</h3>", "<p>Válasz</p>"]


def test_to_iso_utc_handles_zero_date():
    assert ep.to_iso_utc("0000-00-00 00:00:00") is None
    assert ep.to_iso_utc("2021-06-01 10:00:00") == "2021-06-01T10:00:00Z"
