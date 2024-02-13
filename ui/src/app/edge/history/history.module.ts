import { NgModule } from '@angular/core';

import { SharedModule } from '../../shared/shared.module';
import { ChpSocChartComponent } from './chpsoc/chart.component';
import { ChpSocWidgetComponent } from './chpsoc/widget.component';
import { Common } from './common/common';
import { Controller } from './Controller/controller.module';
import { DelayedSellToGridChartComponent } from './delayedselltogrid/chart.component';
import { DelayedSellToGridChartOverviewComponent } from './delayedselltogrid/symmetricpeakshavingchartoverview/delayedselltogridchartoverview.component';
import { DelayedSellToGridWidgetComponent } from './delayedselltogrid/widget.component';
import { FixDigitalOutputChartOverviewComponent } from './fixdigitaloutput/fixdigitaloutputchartoverview/fixdigitaloutputchartoverview.component';
import { FixDigitalOutputSingleChartComponent } from './fixdigitaloutput/singlechart.component';
import { FixDigitalOutputTotalChartComponent } from './fixdigitaloutput/totalchart.component';
import { FixDigitalOutputWidgetComponent } from './fixdigitaloutput/widget.component';
import { GridChartComponent } from './grid/chart.component';
import { GridOptimizedChargeChartComponent } from './gridoptimizedcharge/chart.component';
import { GridOptimizedChargeChartOverviewComponent } from './gridoptimizedcharge/gridoptimizedchargechartoverview/gridoptimizedchargechartoverview.component';
import { SellToGridLimitChartComponent } from './gridoptimizedcharge/sellToGridLimitChart.component';
import { GridOptimizedChargeWidgetComponent } from './gridoptimizedcharge/widget.component';
import { HeatingelementChartComponent } from './heatingelement/chart.component';
import { HeatingelementChartOverviewComponent } from './heatingelement/heatingelementchartoverview/heatingelementchartoverview.component';
import { HeatingelementWidgetComponent } from './heatingelement/widget.component';
import { HeatPumpChartComponent } from './heatpump/chart.component';
import { HeatPumpChartOverviewComponent } from './heatpump/heatpumpchartoverview/heatpumpchartoverview.component';
import { HeatpumpWidgetComponent } from './heatpump/widget.component';
import { HistoryComponent } from './history.component';
import { HistoryParentComponent } from './historyparent.component';
import { AsymmetricPeakshavingChartOverviewComponent } from './peakshaving/asymmetric/asymmetricpeakshavingchartoverview/asymmetricpeakshavingchartoverview.component';
import { AsymmetricPeakshavingChartComponent } from './peakshaving/asymmetric/chart.component';
import { AsymmetricPeakshavingWidgetComponent } from './peakshaving/asymmetric/widget.component';
import { SymmetricPeakshavingChartComponent } from './peakshaving/symmetric/chart.component';
import { SymmetricPeakshavingChartOverviewComponent } from './peakshaving/symmetric/symmetricpeakshavingchartoverview/symmetricpeakshavingchartoverview.component';
import { SymmetricPeakshavingWidgetComponent } from './peakshaving/symmetric/widget.component';
import { TimeslotPeakshavingChartComponent } from './peakshaving/timeslot/chart.component';
import { TimeslotPeakshavingChartOverviewComponent } from './peakshaving/timeslot/timeslotpeakshavingchartoverview/timeslotpeakshavingchartoverview.component';
import { TimeslotPeakshavingWidgetComponent } from './peakshaving/timeslot/widget.component';
import { SinglethresholdChartComponent } from './singlethreshold/chart.component';
import { SinglethresholdChartOverviewComponent } from './singlethreshold/singlethresholdchartoverview/singlethresholdchartoverview.component';
import { SinglethresholdWidgetComponent } from './singlethreshold/widget.component';
import { StorageChargerChartComponent } from './storage/chargerchart.component';
import { StorageESSChartComponent } from './storage/esschart.component';
import { StorageSingleChartComponent } from './storage/singlechart.component';
import { SocStorageChartComponent } from './storage/socchart.component';
import { StorageChartOverviewComponent } from './storage/storagechartoverview/storagechartoverview.component';
import { StorageTotalChartComponent } from './storage/totalchart.component';
import { StorageComponent } from './storage/widget.component';

@NgModule({
  imports: [
    SharedModule,
    Common,
    Controller,
  ],
  declarations: [
    AsymmetricPeakshavingChartComponent,
    AsymmetricPeakshavingChartOverviewComponent,
    AsymmetricPeakshavingWidgetComponent,
    ChpSocChartComponent,
    ChpSocWidgetComponent,
    DelayedSellToGridChartComponent,
    DelayedSellToGridChartOverviewComponent,
    DelayedSellToGridWidgetComponent,
    FixDigitalOutputChartOverviewComponent,
    FixDigitalOutputSingleChartComponent,
    FixDigitalOutputTotalChartComponent,
    FixDigitalOutputWidgetComponent,
    GridChartComponent,
    GridOptimizedChargeChartComponent,
    GridOptimizedChargeChartOverviewComponent,
    GridOptimizedChargeWidgetComponent,
    HeatingelementChartComponent,
    HeatingelementChartOverviewComponent,
    HeatingelementWidgetComponent,
    HeatPumpChartComponent,
    HeatPumpChartOverviewComponent,
    HeatpumpWidgetComponent,
    HistoryComponent,
    SellToGridLimitChartComponent,
    SinglethresholdChartComponent,
    SinglethresholdChartOverviewComponent,
    SinglethresholdWidgetComponent,
    SocStorageChartComponent,
    StorageChargerChartComponent,
    StorageChartOverviewComponent,
    StorageComponent,
    StorageESSChartComponent,
    StorageSingleChartComponent,
    StorageTotalChartComponent,
    SymmetricPeakshavingChartComponent,
    SymmetricPeakshavingChartOverviewComponent,
    SymmetricPeakshavingWidgetComponent,
    TimeslotPeakshavingChartComponent,
    TimeslotPeakshavingChartOverviewComponent,
    TimeslotPeakshavingWidgetComponent,
    HistoryParentComponent,
  ],
})
export class HistoryModule { }
