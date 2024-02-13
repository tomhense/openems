import { NgModule } from "@angular/core";
import { BrowserModule } from "@angular/platform-browser";
import { SharedModule } from "src/app/shared/shared.module";
import { FlatComponent } from "./flat/flat";
import { ModalComponent } from "./modal/modal";
import { ScheduleStateAndPriceChartComponent } from "./modal/statePriceChart";
import { SchedulePowerAndSocChartComponent } from "./modal/powerSocChart";

@NgModule({
    imports: [
        BrowserModule,
        SharedModule,
    ],
    entryComponents: [
        FlatComponent,
        ModalComponent,
        ScheduleStateAndPriceChartComponent,
        SchedulePowerAndSocChartComponent,
    ],
    declarations: [
        FlatComponent,
        ModalComponent,
        ScheduleStateAndPriceChartComponent,
        SchedulePowerAndSocChartComponent,
    ],
    exports: [
        FlatComponent,
    ],
})
export class Controller_Ess_TimeOfUseTariff { }
