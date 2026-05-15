# Quant Road Python Module

This directory contains the standalone Python quant module for Quant Road.

From this directory you can run:

```bash
pip install -r requirements.txt
pip install -e .
python -m quant_road full-daily --strategy-id 1 --notify
```

Default behavior:

- If `--start-date` is omitted, daily sync uses a rolling 5-year window by default.
- If strategy backtest start date is omitted, backtests also default to a rolling 5-year window.
- You can still override either date explicitly when you need a custom history range.

For full project documentation, see the repository root `README.md`.
