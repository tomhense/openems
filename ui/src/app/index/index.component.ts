import { Component } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import { environment } from '../../environments';
import { AuthenticateWithPasswordRequest } from '../shared/jsonrpc/request/authenticateWithPasswordRequest';
import { AuthenticateWithPasswordResponse } from '../shared/jsonrpc/response/authenticateWithPasswordResponse';
import { Edge, Service, Utils, Websocket } from '../shared/shared';

@Component({
  selector: 'index',
  templateUrl: './index.component.html'
})
export class IndexComponent {

  private static readonly EDGE_ID_REGEXP = new RegExp('\\d+');

  public env = environment;

  /**
   * True, if there is no access to any Edge.
   */
  public noEdges: boolean = false;

  public form: FormGroup;
  public filter: string = '';
  public filteredEdges: Edge[] = [];

  private stopOnDestroy: Subject<void> = new Subject<void>();
  public slice: number = 20;

  constructor(
    public websocket: Websocket,
    public utils: Utils,
    private router: Router,
    private service: Service,
    private route: ActivatedRoute,
    private toastController: ToastController,
    private translate: TranslateService,
  ) {

    //Forwarding to device index if there is only 1 edge
    service.metadata
      .pipe(
        takeUntil(this.stopOnDestroy),
        filter(metadata => metadata != null)
      )
      .subscribe(metadata => {
        let edgeIds = Object.keys(metadata.edges);
        this.noEdges = edgeIds.length == 0;
        if (edgeIds.length == 1) {
          let edge = metadata.edges[edgeIds[0]];
          if (edge.isOnline) {
            this.router.navigate(['/device', edge.id]);
          }
        }
        this.updateFilteredEdges();
      })
  }

  ionViewWillEnter() {
    this.service.setCurrentComponent('', this.route);
  }

  updateFilteredEdges() {
    let filter = this.filter.toLowerCase();
    let allEdges = this.service.metadata.value?.edges ?? {};
    this.filteredEdges = Object.keys(allEdges)
      .filter(edgeId => {
        let edge = allEdges[edgeId];
        if (/* name */ edge.id.toLowerCase().includes(filter)
          || /* comment */ edge.comment.toLowerCase().includes(filter)
          || /* producttype */ edge.producttype.toLowerCase().includes(filter)) {
          return true;
        }
        return false;
      })
      .sort((edge1, edge2) => {
        // first: try to compare the number, e.g. 'edge5' < 'edge100'
        let e1match = edge1.match(IndexComponent.EDGE_ID_REGEXP)
        if (e1match != null) {
          let e2match = edge2.match(IndexComponent.EDGE_ID_REGEXP)
          if (e2match != null) {
            let e1 = Number(e1match[0]);
            let e2 = Number(e2match[0]);
            if (!isNaN(e1) && !isNaN(e2)) {
              return e1 - e2;
            }
          }
        }
        // second: apply 'natural sort' 
        return edge1.localeCompare(edge2);
      })
      .map(edgeId => allEdges[edgeId]);
  }

  /**
   * Login to OpenEMS Edge or Backend.
   * 
   * @param param data provided in login form
   */
  public doLogin(param: { username?: string, password: string }) {
    let request = new AuthenticateWithPasswordRequest(param);
    this.websocket.sendRequest(request).then(response => {
      this.handleAuthenticateWithPasswordResponse(response as AuthenticateWithPasswordResponse);
    }).catch(reason => {
      this.service.toast(this.translate.instant('Login.authenticationFailed'), 'danger');
    })
  }

  /**
   * Handles a AuthenticateWithPasswordResponse.
   * 
   * @param message 
   */
  private handleAuthenticateWithPasswordResponse(message: AuthenticateWithPasswordResponse) {
    this.service.handleAuthentication(message.result.token, message.result.user, message.result.edges);
  }

  doInfinite(infiniteScroll) {
    setTimeout(() => {
      this.slice += 5;
      infiniteScroll.target.complete();
    }, 200);
  }

  onDestroy() {
    this.stopOnDestroy.next();
    this.stopOnDestroy.complete();
  }
}
