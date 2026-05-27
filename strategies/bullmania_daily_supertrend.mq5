//+------------------------------------------------------------------+
//| Bullmania Multi-Timeframe Strategy — SuperTrend Edition          |
//| Expert Advisor for MetaTrader 5                                  |
//|                                                                  |
//| Based on Ivan on Tech Bullmania course rules.                    |
//| MoneyLine replaced with SuperTrend (ATR 14, Multiplier 1.98).   |
//|                                                                  |
//| Supports Daily and Weekly strategies automatically:              |
//|   Daily  → 2 tranches, 1x ATR stop, +2R partial TP, 2-bar exit |
//|   Weekly → 3 tranches, 1.0–1.5x ATR stop, +5R/+10R TPs, 3-bar  |
//|                                                                  |
//| Port of the Pine Script v5 strategy to MQL5.                     |
//| The Pine version is kept alongside this file.                    |
//+------------------------------------------------------------------+
#property copyright "Bullmania Strategy"
#property version   "1.00"
#property strict

#include <Trade\Trade.mqh>

//+------------------------------------------------------------------+
//| ENUMS                                                            |
//+------------------------------------------------------------------+

// Campaign state machine — identical to Pine Script version:
//   0 = IDLE          — no position, waiting for SuperTrend flip
//   1 = T1_ENTERED    — tranche 1 placed, waiting for T2 confirmation
//   2 = T2_ENTERED    — weekly only, waiting for T3 confirmation
//   3 = FULL_POSITION — all tranches placed (or ladder-in complete)
//   4 = EXITING       — ladder-out in progress
enum ENUM_CAMP_STATE
{
   STATE_IDLE       = 0,
   STATE_T1_ENTERED = 1,
   STATE_T2_ENTERED = 2,
   STATE_FULL       = 3,
   STATE_EXITING    = 4
};

enum ENUM_TRADE_DIR
{
   DIR_LONG  = 1,
   DIR_SHORT = -1,
   DIR_NONE  = 0
};

enum ENUM_ALLOWED_DIR
{
   ALLOW_LONG  = 0,  // Long Only
   ALLOW_SHORT = 1,  // Short Only
   ALLOW_BOTH  = 2   // Both
};

//+------------------------------------------------------------------+
//| INPUT PARAMETERS                                                 |
//+------------------------------------------------------------------+

// — SuperTrend (MoneyLine proxy) —
input int    InpSTPeriod       = 14;     // SuperTrend ATR Period
input double InpSTMult         = 1.98;   // SuperTrend Multiplier
// Note: SuperTrend source is always (High+Low)/2 in MQL5 (equivalent to hl2)

// — Risk Management —
input double InpRiskPct        = 1.0;    // Risk Per Campaign (% of equity)
input int    InpATRLen         = 14;     // ATR Length (for stop-loss)
input double InpWklyStopMult   = 1.25;   // Weekly Stop ATR Multiplier (1.0–1.5)

// — Daily Profit Targets —
input double InpDlyTpR         = 2.0;    // Daily: Partial TP at R-multiple
input int    InpDlyTpPct       = 50;     // Daily: TP Close % (of each tranche)

// — Weekly Profit Targets (Variation B — Hybrid R Model) —
input double InpWklyTp1R       = 5.0;    // Weekly: 1st TP at R (sell T1)
input double InpWklyTp2R       = 10.0;   // Weekly: 2nd TP at R (sell T2)

// — Common —
input double InpBER            = 1.0;    // Breakeven at R-multiple

// — Chop Filter —
input int    InpChopMax        = 2;      // Max Failed Campaigns before pause
input int    InpChopLookback   = 40;     // Chop Lookback (bars)

// — Direction —
input ENUM_ALLOWED_DIR InpTradeDir = ALLOW_BOTH; // Trade Direction

// — Magic Number (unique ID for this EA's orders) —
input long   InpMagicNumber    = 123456; // Magic Number

//+------------------------------------------------------------------+
//| GLOBAL VARIABLES                                                 |
//+------------------------------------------------------------------+

// Trade helper
CTrade trade;

// Indicator handles
int hATR;           // ATR handle for stop-loss calculation
int hSTATR;         // ATR handle for SuperTrend calculation (separate period)

// SuperTrend state (computed manually, no built-in in MT5)
double g_stUpBand;
double g_stDnBand;
int    g_stTrend;       // 1 = bullish, -1 = bearish
int    g_stTrendPrev;   // previous bar's trend for flip detection

// Campaign state
ENUM_CAMP_STATE g_campState;
ENUM_TRADE_DIR  g_campDir;

// Tranche tracking — entry prices, stop prices, volumes, and flags
double g_t1Entry, g_t2Entry, g_t3Entry;
double g_t1Stop,  g_t2Stop,  g_t3Stop;
double g_t1Qty,   g_t2Qty,   g_t3Qty;
double g_rValue;        // 1R in price terms (= stop distance at entry)

bool   g_t1BE, g_t2BE, g_t3BE;    // breakeven activated
bool   g_t1TP, g_t2TP, g_t3TP;    // partial TP taken
bool   g_t1Active, g_t2Active, g_t3Active;

int    g_t1EntryBar, g_t2EntryBar; // bar index when T1/T2 entered
int    g_exitDay;       // ladder-out bar counter

// Chop filter
int    g_recentFails;
int    g_lastFailBar;

// Timeframe detection
bool   g_isWeekly;
int    g_numTranches;   // 2 for daily, 3 for weekly

// Bar tracking (to ensure we only act once per new bar)
datetime g_lastBarTime;

// Ticket IDs for each tranche (to manage specific positions)
ulong  g_t1Ticket, g_t2Ticket, g_t3Ticket;

//+------------------------------------------------------------------+
//| Expert initialization                                            |
//+------------------------------------------------------------------+
int OnInit()
{
   // Configure the trade helper with our magic number
   trade.SetExpertMagicNumber(InpMagicNumber);
   trade.SetDeviations(10);  // slippage tolerance in points

   // Create ATR indicator handles
   // hATR: for stop-loss calculation (reads previous bar — Fix #4)
   // hSTATR: for SuperTrend band calculation (may use different period)
   hATR = iATR(_Symbol, PERIOD_CURRENT, InpATRLen);
   hSTATR = iATR(_Symbol, PERIOD_CURRENT, InpSTPeriod);
   if(hATR == INVALID_HANDLE || hSTATR == INVALID_HANDLE)
   {
      Print("Failed to create ATR handle(s)");
      return INIT_FAILED;
   }

   // Detect timeframe
   // Daily rules: 2 tranches. Weekly rules: 3 tranches.
   ENUM_TIMEFRAMES tf = Period();
   g_isWeekly = (tf == PERIOD_W1);
   g_numTranches = g_isWeekly ? 3 : 2;

   // Reset all state
   ResetCampaign();
   g_recentFails = 0;
   g_lastFailBar = 0;
   g_lastBarTime = 0;
   g_stTrend     = 1;
   g_stTrendPrev = 1;

   // Initialize SuperTrend bands from current price so the first bar
   // doesn't produce a false flip from comparing against 0.
   double initPrice = (SymbolInfoDouble(_Symbol, SYMBOL_BID) + SymbolInfoDouble(_Symbol, SYMBOL_ASK)) / 2.0;
   g_stUpBand    = initPrice;
   g_stDnBand    = initPrice;

   Print("Bullmania EA initialized — Mode: ", g_isWeekly ? "WEEKLY (3T)" : "DAILY (2T)");
   return INIT_SUCCEEDED;
}

//+------------------------------------------------------------------+
//| Expert deinitialization                                          |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   if(hATR != INVALID_HANDLE)
      IndicatorRelease(hATR);
   if(hSTATR != INVALID_HANDLE)
      IndicatorRelease(hSTATR);
}

//+------------------------------------------------------------------+
//| Reset all campaign state variables                               |
//+------------------------------------------------------------------+
void ResetCampaign()
{
   g_campState = STATE_IDLE;
   g_campDir   = DIR_NONE;

   g_t1Entry = 0; g_t2Entry = 0; g_t3Entry = 0;
   g_t1Stop  = 0; g_t2Stop  = 0; g_t3Stop  = 0;
   g_t1Qty   = 0; g_t2Qty   = 0; g_t3Qty   = 0;
   g_rValue  = 0;

   g_t1BE = false; g_t2BE = false; g_t3BE = false;
   g_t1TP = false; g_t2TP = false; g_t3TP = false;
   g_t1Active = false; g_t2Active = false; g_t3Active = false;

   g_t1EntryBar = 0; g_t2EntryBar = 0;
   g_exitDay    = 0;

   g_t1Ticket = 0; g_t2Ticket = 0; g_t3Ticket = 0;
}

//+------------------------------------------------------------------+
//| Check if a new bar has formed (only act once per bar close)      |
//| This is critical — the Bullmania strategy makes ALL decisions    |
//| on bar close only. Without this gate, the EA would fire on       |
//| every tick, causing duplicate entries and premature exits.        |
//+------------------------------------------------------------------+
bool IsNewBar()
{
   datetime currentBarTime = iTime(_Symbol, PERIOD_CURRENT, 0);
   if(currentBarTime != g_lastBarTime)
   {
      g_lastBarTime = currentBarTime;
      return true;
   }
   return false;
}

//+------------------------------------------------------------------+
//| Get the current bar index (bars since chart start)               |
//| Used for chop filter lookback and tranche entry bar tracking.    |
//+------------------------------------------------------------------+
int GetBarIndex()
{
   return iBars(_Symbol, PERIOD_CURRENT);
}

//+------------------------------------------------------------------+
//| Read ATR value from indicator buffer                             |
//| shift=1 means previous bar's ATR — avoids inflated ATR from     |
//| an extreme breakout candle distorting stop width and position    |
//| size for the entire campaign (Fix #4 from code review).          |
//+------------------------------------------------------------------+
double GetATR(int shift = 1)
{
   double buf[1];
   if(CopyBuffer(hATR, 0, shift, 1, buf) <= 0)
      return 0;
   return buf[0];
}

//+------------------------------------------------------------------+
//| SUPERTREND CALCULATION                                           |
//| Ported from the Pine Script v4 indicator. There is no built-in   |
//| SuperTrend in MT5, so we compute it manually each bar.           |
//|                                                                  |
//| Logic:                                                           |
//|   upBand = hl2 - mult * ATR  (support band in uptrend)          |
//|   dnBand = hl2 + mult * ATR  (resistance band in downtrend)     |
//|   Bands ratchet — upBand only rises, dnBand only falls           |
//|   Trend flips when close crosses the opposite band               |
//+------------------------------------------------------------------+
void CalcSuperTrend()
{
   // We need bars [0] and [1] for the calculation
   double high1  = iHigh(_Symbol, PERIOD_CURRENT, 1);
   double low1   = iLow(_Symbol, PERIOD_CURRENT, 1);
   double close1 = iClose(_Symbol, PERIOD_CURRENT, 1);
   double high0  = iHigh(_Symbol, PERIOD_CURRENT, 0);
   double low0   = iLow(_Symbol, PERIOD_CURRENT, 0);
   double close0 = iClose(_Symbol, PERIOD_CURRENT, 0);

   // Read ATR for SuperTrend from the persistent handle (separate from stop ATR)
   double atrBuf[1];
   if(CopyBuffer(hSTATR, 0, 0, 1, atrBuf) <= 0) return;
   double stATR = atrBuf[0];

   double src = (high0 + low0) / 2.0;  // hl2

   // Compute raw bands
   double upRaw = src - InpSTMult * stATR;
   double dnRaw = src + InpSTMult * stATR;

   // Ratchet bands (only move in favorable direction)
   // upBand: can only rise during an uptrend
   if(close1 > g_stUpBand)
      g_stUpBand = MathMax(upRaw, g_stUpBand);
   else
      g_stUpBand = upRaw;

   // dnBand: can only fall during a downtrend
   if(close1 < g_stDnBand)
      g_stDnBand = MathMin(dnRaw, g_stDnBand);
   else
      g_stDnBand = dnRaw;

   // Save previous trend for flip detection
   g_stTrendPrev = g_stTrend;

   // Determine trend direction
   if(g_stTrend == -1 && close0 > g_stDnBand)
      g_stTrend = 1;    // flip to bullish
   else if(g_stTrend == 1 && close0 < g_stUpBand)
      g_stTrend = -1;   // flip to bearish
   // else: trend unchanged
}

//+------------------------------------------------------------------+
//| POSITION SIZING                                                  |
//| Total risk (1R) is divided by numTranches so the FULL ladder     |
//| equals 1R. Daily: 2 tranches → each is 0.5R.                    |
//| Weekly: 3 tranches → each is ~0.33R.                             |
//| This is why the EA works correctly on both timeframes despite    |
//| having the same code — we divide by the detected tranche count.  |
//+------------------------------------------------------------------+
double CalcTrancheQty(double stopDistPrice)
{
   if(stopDistPrice <= 0) return SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);

   double equity  = AccountInfoDouble(ACCOUNT_EQUITY);
   double riskAmt = equity * InpRiskPct / 100.0;

   // Convert stop distance from price to account currency
   // For forex: lots = riskAmt / (stopDist_points * tick_value / tick_size)
   // For crypto/CFD: lots = riskAmt / (stopDistPrice * contract_size) simplified
   double tickSize  = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_SIZE);
   double tickValue = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_VALUE);
   double lotStep   = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);
   double minLot    = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);
   double maxLot    = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);

   if(tickSize <= 0 || tickValue <= 0) return minLot;

   // How many ticks in our stop distance
   double stopTicks = stopDistPrice / tickSize;
   // Risk per lot = stopTicks * tickValue
   double riskPerLot = stopTicks * tickValue;

   if(riskPerLot <= 0) return minLot;

   double totalLots = riskAmt / riskPerLot;
   double perTranche = totalLots / (double)g_numTranches;

   // Round to lot step
   perTranche = MathFloor(perTranche / lotStep) * lotStep;

   // Clamp to broker limits
   perTranche = MathMax(perTranche, minLot);
   perTranche = MathMin(perTranche, maxLot);

   return perTranche;
}

//+------------------------------------------------------------------+
//| Get stop distance based on timeframe                             |
//| Daily = 1x ATR, Weekly = configurable 1.0–1.5x ATR              |
//+------------------------------------------------------------------+
double GetStopDist()
{
   double atr = GetATR(1);  // previous bar's ATR (Fix #4)
   if(atr <= 0) atr = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_SIZE) * 10;
   double mult = g_isWeekly ? InpWklyStopMult : 1.0;
   return atr * mult;
}

//+------------------------------------------------------------------+
//| Check if trade direction is allowed                              |
//+------------------------------------------------------------------+
bool AllowLong()  { return InpTradeDir == ALLOW_LONG || InpTradeDir == ALLOW_BOTH; }
bool AllowShort() { return InpTradeDir == ALLOW_SHORT || InpTradeDir == ALLOW_BOTH; }

//+------------------------------------------------------------------+
//| Count our open positions (by magic number)                       |
//+------------------------------------------------------------------+
int CountOpenPositions()
{
   int count = 0;
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket > 0 && PositionGetInteger(POSITION_MAGIC) == InpMagicNumber
         && PositionGetString(POSITION_SYMBOL) == _Symbol)
         count++;
   }
   return count;
}

//+------------------------------------------------------------------+
//| Check if a specific ticket is still open                         |
//+------------------------------------------------------------------+
bool IsPositionOpen(ulong ticket)
{
   if(ticket == 0) return false;
   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      if(PositionGetTicket(i) == ticket)
         return true;
   }
   return false;
}

//+------------------------------------------------------------------+
//| Get unrealized profit for a specific position ticket             |
//+------------------------------------------------------------------+
double GetPositionProfit(ulong ticket, double entryPrice)
{
   if(ticket == 0 || !IsPositionOpen(ticket)) return 0;
   if(!PositionSelectByTicket(ticket)) return 0;

   double currentPrice = PositionGetDouble(POSITION_PRICE_CURRENT);
   ENUM_POSITION_TYPE type = (ENUM_POSITION_TYPE)PositionGetInteger(POSITION_TYPE);

   if(type == POSITION_TYPE_BUY)
      return currentPrice - entryPrice;
   else
      return entryPrice - currentPrice;
}

//+------------------------------------------------------------------+
//| Close a position fully                                           |
//+------------------------------------------------------------------+
bool ClosePosition(ulong ticket, string comment)
{
   if(ticket == 0 || !IsPositionOpen(ticket)) return false;
   trade.PositionClose(ticket);
   Print("Closed position #", ticket, " — ", comment);
   return true;
}

//+------------------------------------------------------------------+
//| Close a position partially (by percentage)                       |
//+------------------------------------------------------------------+
bool ClosePartial(ulong ticket, int pctToClose, string comment)
{
   if(ticket == 0 || !IsPositionOpen(ticket)) return false;
   if(!PositionSelectByTicket(ticket)) return false;

   double volume = PositionGetDouble(POSITION_VOLUME);
   double closeVol = volume * pctToClose / 100.0;

   // Round to lot step
   double lotStep = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);
   double minLot  = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);
   closeVol = MathFloor(closeVol / lotStep) * lotStep;
   closeVol = MathMax(closeVol, minLot);

   if(closeVol >= volume)
   {
      // Close all if partial would exceed total
      return ClosePosition(ticket, comment);
   }

   trade.PositionClosePartial(ticket, closeVol);
   Print("Partial close #", ticket, " vol=", closeVol, " — ", comment);
   return true;
}

//+------------------------------------------------------------------+
//| Modify stop loss on an existing position                         |
//+------------------------------------------------------------------+
bool ModifyStopLoss(ulong ticket, double newSL)
{
   if(ticket == 0 || !IsPositionOpen(ticket)) return false;
   if(!PositionSelectByTicket(ticket)) return false;

   double currentTP = PositionGetDouble(POSITION_TP);
   trade.PositionModify(ticket, newSL, currentTP);
   return true;
}

//+------------------------------------------------------------------+
//| Open a new position (long or short)                              |
//+------------------------------------------------------------------+
ulong OpenPosition(ENUM_TRADE_DIR dir, double qty, double stopPrice, string comment)
{
   double price;
   ENUM_ORDER_TYPE orderType;

   if(dir == DIR_LONG)
   {
      price = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
      orderType = ORDER_TYPE_BUY;
   }
   else
   {
      price = SymbolInfoDouble(_Symbol, SYMBOL_BID);
      orderType = ORDER_TYPE_SELL;
   }

   trade.SetExpertMagicNumber(InpMagicNumber);

   if(orderType == ORDER_TYPE_BUY)
      trade.Buy(qty, _Symbol, price, stopPrice, 0, comment);
   else
      trade.Sell(qty, _Symbol, price, stopPrice, 0, comment);

   ulong ticket = trade.ResultOrder();
   if(ticket > 0)
      Print("Opened ", (dir == DIR_LONG ? "LONG" : "SHORT"), " #", ticket,
            " qty=", qty, " stop=", stopPrice, " — ", comment);
   else
      Print("Order FAILED: ", trade.ResultRetcodeDescription());

   return ticket;
}

//+------------------------------------------------------------------+
//| CHOP FILTER                                                      |
//| After N failed campaigns in a lookback window, pause entries.    |
//| This protects against choppy markets that keep stopping out.     |
//+------------------------------------------------------------------+
bool IsChopFiltered()
{
   // Decay old failures
   int currentBar = GetBarIndex();
   if(currentBar - g_lastFailBar > InpChopLookback && g_recentFails > 0)
      g_recentFails = 0;

   return (g_recentFails >= InpChopMax);
}

//+------------------------------------------------------------------+
//| Record a failed campaign (for chop filter)                       |
//+------------------------------------------------------------------+
void RecordFailure()
{
   g_recentFails++;
   g_lastFailBar = GetBarIndex();
   Print("Campaign failure recorded. Fails: ", g_recentFails, "/", InpChopMax);
}

//+------------------------------------------------------------------+
//| Check for stopped-out tranches and update state                  |
//| If a tranche ticket is no longer open, mark it inactive.         |
//| If ALL tranches are closed, reset the campaign.                  |
//+------------------------------------------------------------------+
void CheckClosedTranches()
{
   bool anyChanged = false;

   // Check each active tranche
   if(g_t1Active && !IsPositionOpen(g_t1Ticket))
   {
      g_t1Active = false;
      anyChanged = true;
      Print("T1 #", g_t1Ticket, " closed (stop or external)");
   }
   if(g_t2Active && !IsPositionOpen(g_t2Ticket))
   {
      g_t2Active = false;
      anyChanged = true;
      Print("T2 #", g_t2Ticket, " closed (stop or external)");
   }
   if(g_t3Active && !IsPositionOpen(g_t3Ticket))
   {
      g_t3Active = false;
      anyChanged = true;
      Print("T3 #", g_t3Ticket, " closed (stop or external)");
   }

   // If a tranche was closed unexpectedly (e.g., stop hit), check if it was a loss
   if(anyChanged && g_campState != STATE_IDLE)
   {
      // Check deal history for losses
      CheckForLosses();
   }

   // If all tranches are now closed, reset campaign
   if(g_campState != STATE_IDLE && !g_t1Active && !g_t2Active && !g_t3Active)
   {
      Print("All tranches closed — resetting campaign");
      ResetCampaign();
   }
}

//+------------------------------------------------------------------+
//| Check recent deal history for losses (for chop filter)           |
//+------------------------------------------------------------------+
void CheckForLosses()
{
   // Look at the most recent closed deal
   HistorySelect(0, TimeCurrent());
   int totalDeals = HistoryDealsTotal();
   if(totalDeals <= 0) return;

   ulong dealTicket = HistoryDealGetTicket(totalDeals - 1);
   if(dealTicket <= 0) return;

   // Only count our EA's deals
   if(HistoryDealGetInteger(dealTicket, DEAL_MAGIC) != InpMagicNumber) return;
   if(HistoryDealGetString(dealTicket, DEAL_SYMBOL) != _Symbol) return;

   // Check if it was an exit (not an entry)
   ENUM_DEAL_ENTRY entry = (ENUM_DEAL_ENTRY)HistoryDealGetInteger(dealTicket, DEAL_ENTRY);
   if(entry == DEAL_ENTRY_OUT || entry == DEAL_ENTRY_OUT_BY)
   {
      double profit = HistoryDealGetDouble(dealTicket, DEAL_PROFIT);
      if(profit < 0)
         RecordFailure();
   }
}

//+------------------------------------------------------------------+
//| ENTRY LOGIC — TRANCHE 1                                         |
//| Enter on SuperTrend flip (bullish for longs, bearish for shorts) |
//+------------------------------------------------------------------+
void EntryT1()
{
   if(g_campState != STATE_IDLE) return;
   if(IsChopFiltered()) return;

   bool bullFlip = (g_stTrend == 1  && g_stTrendPrev == -1);
   bool bearFlip = (g_stTrend == -1 && g_stTrendPrev == 1);

   double sd = GetStopDist();
   if(sd <= 0) return;

   double price = iClose(_Symbol, PERIOD_CURRENT, 0);
   double qty;

   if(bullFlip && AllowLong())
   {
      g_rValue  = sd;
      qty       = CalcTrancheQty(sd);
      g_t1Entry = price;
      g_t1Stop  = price - g_rValue;
      g_t1Qty   = qty;
      g_campDir = DIR_LONG;

      g_t1Ticket = OpenPosition(DIR_LONG, qty, g_t1Stop, "Long_T1");
      if(g_t1Ticket > 0)
      {
         g_t1Active   = true;
         g_t1EntryBar = GetBarIndex();
         g_campState  = STATE_T1_ENTERED;
         Print("T1 LONG entered at ", price, " stop=", g_t1Stop, " 1R=", g_rValue);
      }
   }
   else if(bearFlip && AllowShort())
   {
      g_rValue  = sd;
      qty       = CalcTrancheQty(sd);
      g_t1Entry = price;
      g_t1Stop  = price + g_rValue;
      g_t1Qty   = qty;
      g_campDir = DIR_SHORT;

      g_t1Ticket = OpenPosition(DIR_SHORT, qty, g_t1Stop, "Short_T1");
      if(g_t1Ticket > 0)
      {
         g_t1Active   = true;
         g_t1EntryBar = GetBarIndex();
         g_campState  = STATE_T1_ENTERED;
         Print("T1 SHORT entered at ", price, " stop=", g_t1Stop, " 1R=", g_rValue);
      }
   }
}

//+------------------------------------------------------------------+
//| ENTRY LOGIC — TRANCHE 2                                         |
//| Next bar after T1 if SuperTrend still confirms direction         |
//+------------------------------------------------------------------+
void EntryT2()
{
   if(g_campState != STATE_T1_ENTERED) return;
   if(GetBarIndex() <= g_t1EntryBar) return;  // must be a new bar after T1

   double price = iClose(_Symbol, PERIOD_CURRENT, 0);
   double qty   = CalcTrancheQty(g_rValue);

   if(g_campDir == DIR_LONG && g_stTrend == 1)
   {
      g_t2Entry = price;
      g_t2Stop  = price - g_rValue;
      g_t2Qty   = qty;

      g_t2Ticket = OpenPosition(DIR_LONG, qty, g_t2Stop, "Long_T2");
      if(g_t2Ticket > 0)
      {
         g_t2Active   = true;
         g_t2EntryBar = GetBarIndex();
         // Daily: go straight to FULL. Weekly: wait for T3.
         g_campState  = g_isWeekly ? STATE_T2_ENTERED : STATE_FULL;
         Print("T2 LONG entered at ", price, " stop=", g_t2Stop);
      }
   }
   else if(g_campDir == DIR_SHORT && g_stTrend == -1)
   {
      g_t2Entry = price;
      g_t2Stop  = price + g_rValue;
      g_t2Qty   = qty;

      g_t2Ticket = OpenPosition(DIR_SHORT, qty, g_t2Stop, "Short_T2");
      if(g_t2Ticket > 0)
      {
         g_t2Active   = true;
         g_t2EntryBar = GetBarIndex();
         g_campState  = g_isWeekly ? STATE_T2_ENTERED : STATE_FULL;
         Print("T2 SHORT entered at ", price, " stop=", g_t2Stop);
      }
   }
   else
   {
      // SuperTrend flipped against before T2 — manage T1 only
      g_campState = STATE_FULL;
      Print("T2 skipped — SuperTrend flipped against direction");
   }
}

//+------------------------------------------------------------------+
//| ENTRY LOGIC — TRANCHE 3 (Weekly only)                           |
//| Next bar after T2 if SuperTrend still confirms direction         |
//+------------------------------------------------------------------+
void EntryT3()
{
   if(!g_isWeekly) return;
   if(g_campState != STATE_T2_ENTERED) return;
   if(GetBarIndex() <= g_t2EntryBar) return;

   double price = iClose(_Symbol, PERIOD_CURRENT, 0);
   double qty   = CalcTrancheQty(g_rValue);

   if(g_campDir == DIR_LONG && g_stTrend == 1)
   {
      g_t3Entry = price;
      g_t3Stop  = price - g_rValue;
      g_t3Qty   = qty;

      g_t3Ticket = OpenPosition(DIR_LONG, qty, g_t3Stop, "Long_T3");
      if(g_t3Ticket > 0)
      {
         g_t3Active  = true;
         g_campState = STATE_FULL;
         Print("T3 LONG entered at ", price, " stop=", g_t3Stop);
      }
   }
   else if(g_campDir == DIR_SHORT && g_stTrend == -1)
   {
      g_t3Entry = price;
      g_t3Stop  = price + g_rValue;
      g_t3Qty   = qty;

      g_t3Ticket = OpenPosition(DIR_SHORT, qty, g_t3Stop, "Short_T3");
      if(g_t3Ticket > 0)
      {
         g_t3Active  = true;
         g_campState = STATE_FULL;
         Print("T3 SHORT entered at ", price, " stop=", g_t3Stop);
      }
   }
   else
   {
      // SuperTrend flipped against before T3 — manage T1+T2 only
      g_campState = STATE_FULL;
      Print("T3 skipped — SuperTrend flipped against direction");
   }
}

//+------------------------------------------------------------------+
//| PROFIT PROTECTION — BREAKEVEN AT +1R                             |
//| Once profit reaches beR * rValue, move stop to entry price.      |
//| Campaign becomes risk-free from this point.                      |
//+------------------------------------------------------------------+
void ManageBreakeven()
{
   if(g_campState != STATE_FULL) return;
   if(g_rValue <= 0) return;

   double beTarget = g_rValue * InpBER;

   // T1
   if(g_t1Active && !g_t1BE && g_t1Entry > 0)
   {
      double prof = GetPositionProfit(g_t1Ticket, g_t1Entry);
      if(prof >= beTarget)
      {
         g_t1Stop = g_t1Entry;
         g_t1BE   = true;
         ModifyStopLoss(g_t1Ticket, g_t1Stop);
         Print("T1 stop moved to breakeven at ", g_t1Stop);
      }
   }

   // T2
   if(g_t2Active && !g_t2BE && g_t2Entry > 0)
   {
      double prof = GetPositionProfit(g_t2Ticket, g_t2Entry);
      if(prof >= beTarget)
      {
         g_t2Stop = g_t2Entry;
         g_t2BE   = true;
         ModifyStopLoss(g_t2Ticket, g_t2Stop);
         Print("T2 stop moved to breakeven at ", g_t2Stop);
      }
   }

   // T3 (weekly only)
   if(g_t3Active && !g_t3BE && g_t3Entry > 0)
   {
      double prof = GetPositionProfit(g_t3Ticket, g_t3Entry);
      if(prof >= beTarget)
      {
         g_t3Stop = g_t3Entry;
         g_t3BE   = true;
         ModifyStopLoss(g_t3Ticket, g_t3Stop);
         Print("T3 stop moved to breakeven at ", g_t3Stop);
      }
   }
}

//+------------------------------------------------------------------+
//| PARTIAL TAKE PROFIT                                              |
//| Daily:  close 50% of each tranche at +2R                        |
//| Weekly: close T1 at +5R, close T2 at +10R, hold T3 until flip   |
//+------------------------------------------------------------------+
void ManageTakeProfit()
{
   if(g_campState != STATE_FULL) return;
   if(g_rValue <= 0) return;

   if(g_isWeekly)
   {
      // --- Weekly Hybrid R Model (Variation B) ---
      // T1: sell entire tranche at +5R
      if(g_t1Active && !g_t1TP && g_t1Entry > 0)
      {
         double prof = GetPositionProfit(g_t1Ticket, g_t1Entry);
         if(prof >= g_rValue * InpWklyTp1R)
         {
            g_t1TP = true;
            ClosePosition(g_t1Ticket, "TP_T1_5R");
            g_t1Active = false;
         }
      }

      // T2: sell entire tranche at +10R
      if(g_t2Active && !g_t2TP && g_t2Entry > 0)
      {
         double prof = GetPositionProfit(g_t2Ticket, g_t2Entry);
         if(prof >= g_rValue * InpWklyTp2R)
         {
            g_t2TP = true;
            ClosePosition(g_t2Ticket, "TP_T2_10R");
            g_t2Active = false;
         }
      }
      // T3: held until SuperTrend flip (ladder-out handles this)
   }
   else
   {
      // --- Daily Hybrid R Model ---
      // Close InpDlyTpPct% of each tranche at +2R
      if(g_t1Active && !g_t1TP && g_t1Entry > 0)
      {
         double prof = GetPositionProfit(g_t1Ticket, g_t1Entry);
         if(prof >= g_rValue * InpDlyTpR)
         {
            g_t1TP = true;
            ClosePartial(g_t1Ticket, InpDlyTpPct, "TP_T1_2R");
         }
      }

      if(g_t2Active && !g_t2TP && g_t2Entry > 0)
      {
         double prof = GetPositionProfit(g_t2Ticket, g_t2Entry);
         if(prof >= g_rValue * InpDlyTpR)
         {
            g_t2TP = true;
            ClosePartial(g_t2Ticket, InpDlyTpPct, "TP_T2_2R");
         }
      }
   }
}

//+------------------------------------------------------------------+
//| LADDER-OUT EXIT                                                  |
//| When SuperTrend flips against the campaign direction, exit        |
//| gradually over consecutive bars (one tranche per bar).           |
//|                                                                  |
//| Daily:  Day 1 → T1, Day 2 → T2                                  |
//| Weekly: Day 1 → T1, Day 2 → T2, Day 3 → T3                     |
//|                                                                  |
//| Fix #1 from review: each exitDay now targets exactly ONE         |
//| tranche. No more double-closing T2+T3 on the same bar.          |
//|                                                                  |
//| If SuperTrend flips back during ladder-out, cancel and hold.     |
//+------------------------------------------------------------------+
void ManageLadderOut()
{
   bool bullFlip = (g_stTrend == 1  && g_stTrendPrev == -1);
   bool bearFlip = (g_stTrend == -1 && g_stTrendPrev == 1);

   // --- Start ladder-out on flip against ---
   if(g_campState == STATE_FULL)
   {
      bool flipAgainst = (g_campDir == DIR_LONG && bearFlip) ||
                         (g_campDir == DIR_SHORT && bullFlip);
      if(flipAgainst)
      {
         g_exitDay   = 1;
         g_campState = STATE_EXITING;

         // Day 1: close T1
         if(g_t1Active)
         {
            ClosePosition(g_t1Ticket, "Exit_T1");
            g_t1Active = false;
         }
         Print("Ladder-out started — Day 1, closed T1");
      }
   }

   // --- Continue ladder-out ---
   if(g_campState == STATE_EXITING)
   {
      // Check if SuperTrend flipped back in our favor
      bool flipBack = (g_campDir == DIR_LONG && bullFlip) ||
                      (g_campDir == DIR_SHORT && bearFlip);
      if(flipBack)
      {
         // Cancel ladder-out, hold remaining tranches
         g_campState = STATE_FULL;
         g_exitDay   = 0;
         Print("Ladder-out CANCELLED — SuperTrend flipped back, holding remaining");
         return;
      }

      g_exitDay++;

      // Day 2: close T2
      if(g_exitDay == 2)
      {
         if(g_t2Active)
         {
            ClosePosition(g_t2Ticket, "Exit_T2");
            g_t2Active = false;
         }
         Print("Ladder-out Day 2 — closed T2");

         // Daily: also close T3 if somehow active (safety net)
         if(!g_isWeekly && g_t3Active)
         {
            ClosePosition(g_t3Ticket, "Exit_T3");
            g_t3Active = false;
         }
      }

      // Day 3 (weekly only): close T3
      if(g_exitDay == 3 && g_isWeekly)
      {
         if(g_t3Active)
         {
            ClosePosition(g_t3Ticket, "Exit_T3");
            g_t3Active = false;
         }
         Print("Ladder-out Day 3 — closed T3");
      }
   }
}

//+------------------------------------------------------------------+
//| MAIN TICK HANDLER                                                |
//| All logic runs once per new bar (on close), matching the         |
//| Bullmania rule that decisions are only made on bar close.        |
//+------------------------------------------------------------------+
void OnTick()
{
   // Only process on new bar — the Bullmania strategy decides on bar close only.
   // Without this gate we'd fire on every tick causing chaos.
   if(!IsNewBar()) return;

   // Step 1: Calculate SuperTrend for this bar
   CalcSuperTrend();

   // Step 2: Check for closed tranches (stops hit externally by broker)
   CheckClosedTranches();

   // Step 3: If we have an active campaign, manage it
   if(g_campState == STATE_FULL)
   {
      // 3a: Move stops to breakeven at +1R
      ManageBreakeven();
      // 3b: Take partial profits at target R-multiples
      ManageTakeProfit();
   }

   // Step 4: Ladder-out exit logic (must run before entry to avoid conflicts)
   ManageLadderOut();

   // Step 5: Entry logic — ladder in tranches
   EntryT1();
   EntryT2();
   EntryT3();
}

//+------------------------------------------------------------------+
//| END                                                              |
//+------------------------------------------------------------------+
