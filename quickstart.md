


## /scan


```json

    {
      "timeframe": "1D",
      "operator": "AND"   ,
      "conditions": [
        {
              "indicatorType": "SUPERTREND",
              "state": "BULLISH",
              "maxDaysSinceFlip": 10
            },
        {
          "indicatorType": "RSI",
          "event": "CROSSED_ABOVE_60",
          "maxDaysSinceCross": 10
        }
      ]
    }

```


```json

    {
      "timeframe": "1D",
      "operator": "AND",
      "conditions": [
        {
              "indicatorType": "SUPERTREND",
              "state": "BEARISH",
              "maxDaysSinceFlip": 10
            },
        {
          "indicatorType": "RSI",
          "event": "CROSSED_BELOW_40",
          "maxDaysSinceCross": 10
        }
      ]
    }

```