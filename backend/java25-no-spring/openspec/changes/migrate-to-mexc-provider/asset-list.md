# Final MEXC onboarding list

Derived from the user-supplied top-100-by-market-cap lists (crypto, stock, ETF), each cross-checked
live against MEXC's `/api/v3/exchangeInfo` on 2026-08-26, then capped at 50/50/no-cap per
[design.md Decision 5](design.md#decisions) (crypto and stock trimmed to the top 50 *tradeable*
names by market-cap rank; ETF kept at its full matched count of 18, well under any cap).

Total: **118 assets** (50 CRYPTO + 50 STOCK + 18 ETF).

Dropped from the user's lists entirely (no MEXC listing at any rank, or excluded from crypto as
non-trend-bearing pegged assets): see proposal.md / chat history for the full drop lists. Reserves
(next tradeable by rank, in case a top-50 pick is later found untradeable at seed time) are noted
per section below.

## CRYPTO (50) — `asset_class = CRYPTO`, `venue = MEXC`

Direct ticker remap, `<TICKER>USDT` on both the source ranking and MEXC — no wrapper involved.

| # | Symbol (MEXC) | Name |
|---|---|---|
| 1 | BTCUSDT | Bitcoin |
| 2 | ETHUSDT | Ethereum |
| 3 | BNBUSDT | BNB |
| 4 | XRPUSDT | XRP |
| 5 | SOLUSDT | Solana |
| 6 | TRXUSDT | TRON |
| 7 | HYPEUSDT | Hyperliquid |
| 8 | DOGEUSDT | Dogecoin |
| 9 | ZECUSDT | Zcash |
| 10 | WBTUSDT | WhiteBIT Coin |
| 11 | LINKUSDT | Chainlink |
| 12 | XMRUSDT | Monero |
| 13 | ADAUSDT | Cardano |
| 14 | XLMUSDT | Stellar |
| 15 | BCHUSDT | Bitcoin Cash |
| 16 | CCUSDT | Canton |
| 17 | GRAMUSDT | Gram (prev. Toncoin) |
| 18 | LTCUSDT | Litecoin |
| 19 | HBARUSDT | Hedera |
| 20 | AVAXUSDT | Avalanche |
| 21 | SHIBUSDT | Shiba Inu |
| 22 | SUIUSDT | Sui |
| 23 | CROUSDT | Cronos |
| 24 | UNIUSDT | Uniswap |
| 25 | MUSDT | MemeCore |
| 26 | NEARUSDT | NEAR Protocol |
| 27 | OKBUSDT | OKB |
| 28 | TAOUSDT | Bittensor |
| 29 | AAVEUSDT | Aave |
| 30 | ASTERUSDT | Aster |
| 31 | WLFIUSDT | World Liberty Financial |
| 32 | ONDOUSDT | Ondo |
| 33 | PUMPUSDT | Pump.fun |
| 34 | MNTUSDT | Mantle |
| 35 | MORPHOUSDT | Morpho |
| 36 | PEPEUSDT | Pepe |
| 37 | SKYUSDT | Sky |
| 38 | HTXUSDT | HTX DAO |
| 39 | DOTUSDT | Polkadot |
| 40 | WLDUSDT | Worldcoin |
| 41 | ENAUSDT | Ethena |
| 42 | ICPUSDT | Internet Computer |
| 43 | BGBUSDT | Bitget Token |
| 44 | POLUSDT | POL (ex-MATIC) |
| 45 | UUSDT | United Stables |
| 46 | ETCUSDT | Ethereum Classic |
| 47 | BTWUSDT | Bitway |
| 48 | PIUSDT | Pi Network |
| 49 | KCSUSDT | KuCoin |
| 50 | QNTUSDT | Quant |

Reserves (ranks 51+, tradeable, in order): NEXO, VVV, LIT, JST, ALGO, ATOM, RENDER, KAS, STABLE, JUP...

Dropped as non-tradeable/non-trend-bearing (never candidates): LEO, DAI, XAUT, PAXG, GT, GHO, EURC
(stablecoins/gold-pegged tokens have no MEXC USDT pair and/or no meaningful "trend"; LEO/GT are
rival exchanges' own tokens, not listed on MEXC).

## STOCK (50) — `asset_class = STOCK`, `venue = MEXC`

Ondo-wrapped (`-ON` suffix) tokenized equities, chosen over the parallel `-X` "xStock" wrapper
where both exist (NVDA, AAPL, GOOG, AMZN, META, TSLA, SPCX all have both — `-ON` picked for
consistency; revisit by liquidity if one wrapper turns out much thinner than the other).

| # | Symbol (MEXC) | Underlying | Name |
|---|---|---|---|
| 1 | NVDAONUSDT | NVDA | NVIDIA |
| 2 | AAPLONUSDT | AAPL | Apple |
| 3 | GOOGLONUSDT | GOOG* | Alphabet Class A (*substitutes for GOOG/Class C — MEXC only wraps Class A) |
| 4 | MSFTONUSDT | MSFT | Microsoft |
| 5 | AMZNONUSDT | AMZN | Amazon |
| 6 | TSMONUSDT | TSM | Taiwan Semiconductor |
| 7 | SPCXONUSDT | SPCX | SpaceX |
| 8 | AVGOONUSDT | AVGO | Broadcom |
| 9 | METAONUSDT | META | Meta Platforms |
| 10 | TSLAONUSDT | TSLA | Tesla |
| 11 | LLYONUSDT | LLY | Eli Lilly |
| 12 | MUONUSDT | MU | Micron Technology |
| 13 | JPMONUSDT | JPM | JPMorgan Chase |
| 14 | SKHYONUSDT | SKHY | SK hynix |
| 15 | WMTONUSDT | WMT | Walmart |
| 16 | AMDONUSDT | AMD | AMD |
| 17 | VONUSDT | V | Visa |
| 18 | ASMLONUSDT | ASML | ASML Holding |
| 19 | XOMONUSDT | XOM | Exxon Mobil |
| 20 | JNJONUSDT | JNJ | Johnson & Johnson |
| 21 | MAONUSDT | MA | Mastercard |
| 22 | ABBVONUSDT | ABBV | AbbVie |
| 23 | INTCONUSDT | INTC | Intel |
| 24 | CSCOONUSDT | CSCO | Cisco Systems |
| 25 | BACONUSDT | BAC | Bank of America |
| 26 | COSTONUSDT | COST | Costco |
| 27 | ORCLONUSDT | ORCL | Oracle |
| 28 | PLTRONUSDT | PLTR | Palantir |
| 29 | CVXONUSDT | CVX | Chevron |
| 30 | KOONUSDT | KO | Coca-Cola |
| 31 | LRCXONUSDT | LRCX | Lam Research |
| 32 | MRKONUSDT | MRK | Merck |
| 33 | AMATONUSDT | AMAT | Applied Materials |
| 34 | CATONUSDT | CAT | Caterpillar |
| 35 | GEONUSDT | GE | GE Aerospace |
| 36 | UNHONUSDT | UNH | UnitedHealth |
| 37 | NFLXONUSDT | NFLX | Netflix |
| 38 | PGONUSDT | PG | Procter & Gamble |
| 39 | GSONUSDT | GS | Goldman Sachs |
| 40 | DELLONUSDT | DELL | Dell Technologies |
| 41 | RTXONUSDT | RTX | RTX |
| 42 | BABAONUSDT | BABA | Alibaba |
| 43 | PANWONUSDT | PANW | Palo Alto Networks |
| 44 | ARMONUSDT | ARM | Arm Holdings |
| 45 | WFCONUSDT | WFC | Wells Fargo |
| 46 | SAPONUSDT | SAP | SAP |
| 47 | GEVONUSDT | GEV | GE Vernova |
| 48 | ANETONUSDT | ANET | Arista Networks |
| 49 | KLACONUSDT | KLAC | KLA |
| 50 | AMGNONUSDT | AMGN | Amgen |

Reserves (ranks 51+, tradeable, in order): TXN, TMO, AXP, LIN, C, IBM, SNDK, NVO, MRVL, VZ, ABT, SHOP, APH, TMUS.

Dropped — no MEXC tokenized wrapper at any rank (30 non-US primary listings + 4 surprising US
misses): Saudi Aramco (2222), Samsung (005930), SK hynix's native listing (000660 — its `SKHY`
ADR-style ticker *is* onboarded, row 14 above), Tencent (700), 8 Chinese A-shares (601398, 601288,
601857, 601939, 601988, 300750, 600519, 600941), Roche (RO), HSBC, Novartis (NVS), Royal Bank of
Canada (RY), AstraZeneca (AZN), LVMH (MC), Nestle (NESN), Shell (SHEL), Siemens (SIE), Mitsubishi
UFJ (MUFG), BHP, L'Oreal (OR), Toyota (TM), IHC, Santander (SAN), Inditex (ITX), TD Bank (TD),
Allianz (ALV), TotalEnergies (TTE), Schneider Electric (SU), and **Berkshire Hathaway (BRK.A),
Morgan Stanley (MS), Home Depot (HD), Philip Morris (PM)**.

## ETF (18) — `asset_class = ETF`, `venue = MEXC`

All matched tickers kept — well under the 50 cap, no trimming needed.

| # | Symbol (MEXC) | Underlying | Name |
|---|---|---|---|
| 1 | IVVONUSDT | IVV | iShares Core S&P 500 ETF |
| 2 | SPYONUSDT | SPY | SPDR S&P 500 ETF |
| 3 | QQQONUSDT | QQQ | Invesco QQQ Trust |
| 4 | IEFAONUSDT | IEFA | iShares Core MSCI EAFE ETF |
| 5 | IEMGONUSDT | IEMG | iShares Core MSCI Emerging Markets ETF |
| 6 | GLDONUSDT | GLD | SPDR Gold Shares |
| 7 | AGGONUSDT | AGG | iShares Core U.S. Aggregate Bond ETF |
| 8 | IJHONUSDT | IJH | iShares Core S&P Mid-Cap ETF |
| 9 | IWFONUSDT | IWF | iShares Russell 1000 Growth ETF |
| 10 | SGOVONUSDT | SGOV | iShares 0-3 Month Treasury Bond ETF |
| 11 | ITOTONUSDT | ITOT | iShares Core S&P Total U.S. Stock Market ETF |
| 12 | IWMONUSDT | IWM | iShares Russell 2000 ETF |
| 13 | EFAONUSDT | EFA | iShares MSCI EAFE ETF |
| 14 | IAUONUSDT | IAU | iShares Gold Trust |
| 15 | TLTONUSDT | TLT | iShares 20+ Year Treasury Bond ETF |
| 16 | SOXXONUSDT | SOXX | iShares Semiconductor ETF |
| 17 | TQQQONUSDT | TQQQ | ProShares UltraPro QQQ |
| 18 | SLVONUSDT | SLV | iShares Silver Trust |

Dropped — no MEXC tokenized wrapper (82 of 100, including VOO, VTI, QQQM, SCHD, the full
Vanguard/Schwab lineup, every bond fund besides AGG/SGOV, the Japan/Taiwan-listed funds, and the
Ireland-domiciled UCITS composite codes like CSTNL/IRRRF/VFAWF).
