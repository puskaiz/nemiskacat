import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "export_orders", Path(__file__).parent / "export-orders.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def test_line_gross_adds_tax():
    assert m.line_gross("3700", "999") == 4699


def test_unit_gross_divides_by_qty():
    assert m.unit_gross(7400, 2) == 3700


def test_tax_rate_from_amounts():
    assert m.tax_rate("999", "3700") == 27
    assert m.tax_rate("0", "0") == 0


def test_to_huf_rounds():
    assert m.to_huf("8390.0") == 8390
    assert m.to_huf("") is None
