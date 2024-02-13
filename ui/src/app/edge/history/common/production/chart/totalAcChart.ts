import { Component } from '@angular/core';
import { AbstractHistoryChart } from 'src/app/shared/genericComponents/chart/abstracthistorychart';
import { QueryHistoricTimeseriesEnergyResponse } from 'src/app/shared/jsonrpc/response/queryHistoricTimeseriesEnergyResponse';
import { ChartAxis, HistoryUtils, YAxisTitle } from 'src/app/shared/service/utils';

import { ChannelAddress } from '../../../../../shared/shared';

@Component({
  selector: 'productionTotalAcChart',
  templateUrl: '../../../../../shared/genericComponents/chart/abstracthistorychart.html',
})
export class TotalAcChartComponent extends AbstractHistoryChart {

  protected override getChartData(): HistoryUtils.ChartData {
    return {
      input:
        [
          {
            name: 'ProductionAcActivePower',
            powerChannel: ChannelAddress.fromString('_sum/ProductionAcActivePower'),
            energyChannel: ChannelAddress.fromString('_sum/ProductionAcActiveEnergy'),
          },
          {
            name: 'ProductionAcActivePowerL1',
            powerChannel: ChannelAddress.fromString('_sum/ProductionAcActivePowerL1'),
          },
          {
            name: 'ProductionAcActivePowerL2',
            powerChannel: ChannelAddress.fromString('_sum/ProductionAcActivePowerL2'),
          },
          {
            name: 'ProductionAcActivePowerL3',
            powerChannel: ChannelAddress.fromString('_sum/ProductionAcActivePowerL3'),
          },
        ],
      output: (data: HistoryUtils.ChannelData) => {
        let datasets: HistoryUtils.DisplayValues[] = [];

        datasets.push({
          name: this.translate.instant("General.TOTAL"),
          nameSuffix: (energyPeriodResponse: QueryHistoricTimeseriesEnergyResponse) => {
            return energyPeriodResponse.result.data['_sum/ProductionAcActiveEnergy'] ?? null;
          },
          converter: () => {
            return data['ProductionAcActivePower'];
          },
          color: "rgb(0,152,204)",
          stack: 0,
        });

        for (let i = 1; i < 4; i++) {
          datasets.push({
            name: "Phase L" + i,
            converter: () => {
              if (!this.showPhases) {
                return null;
              }
              return data['ProductionAcActivePowerL' + i] ?? null;
            },
            color: 'rgb(' + AbstractHistoryChart.phaseColors[i - 1] + ')',
          });
        }

        return datasets;
      },
      tooltip: {
        formatNumber: '1.1-2',
      },
      yAxes: [{
        unit: YAxisTitle.ENERGY,
        position: 'left',
        yAxisId: ChartAxis.LEFT,
      }],
    };
  }
}
