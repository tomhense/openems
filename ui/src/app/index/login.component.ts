import { AfterContentChecked, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { environment } from 'src/environments';

import { AuthenticateWithPasswordRequest } from '../shared/jsonrpc/request/authenticateWithPasswordRequest';
import { Edge, Service, Utils, Websocket } from '../shared/shared';

@Component({
  selector: 'login',
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit, AfterContentChecked, OnDestroy {
  public environment = environment;
  public form: FormGroup;
  private stopOnDestroy: Subject<void> = new Subject<void>();
  private page = 0;
  protected formIsDisabled: boolean = false;

  constructor(
    public service: Service,
    public websocket: Websocket,
    public utils: Utils,
    private router: Router,
    private route: ActivatedRoute,
    private cdref: ChangeDetectorRef,
  ) { }

  ngAfterContentChecked() {
    this.cdref.detectChanges();
  }

  ngOnInit() {

    // TODO add websocket status observable
    const interval = setInterval(() => {
      if (this.websocket.status === 'online') {
        this.router.navigate(['/overview']);
        clearInterval(interval);
      }
    }, 1000);

    this.service.setCurrentComponent('', this.route);
  }

  async ionViewWillEnter() {

    // Execute Login-Request if url path matches 'demo'
    if (this.route.snapshot.routeConfig.path == 'demo') {

      await new Promise((resolve) => setTimeout(() => {

        // Wait for Websocket
        if (this.websocket.status == 'waiting for credentials') {
          this.service.startSpinner('loginspinner');
          let lang = this.route.snapshot.queryParamMap.get('lang') ?? null;
          if (lang) {
            localStorage.DEMO_LANGUAGE = lang;
          }
          resolve(
            this.doDemoLogin({ username: 'demo', password: 'demo' }));
        }
      }, 2000)).then(() => { this.service.setCurrentComponent('', this.route); });
    } else {
      localStorage.removeItem('DEMO_LANGUAGE');
    }
  }

  /**
   * Trims credentials
   *
   * @param password the password
   * @param username the username
   * @returns trimmed credentials
   */
  public static trimCredentials(password: string, username?: string): { password: string, username?: string } {
    return {
      password: password?.trim(),
      ...(username && { username: username?.trim() }),
    };
  }

  /**
   * Login to OpenEMS Edge or Backend.
   *
   * @param param data provided in login form
   */
  public doLogin(param: { username?: string, password: string }) {

    param = LoginComponent.trimCredentials(param.password, param.username);

    // Prevent that user submits via keyevent 'enter' multiple times
    if (this.formIsDisabled) {
      return;
    }

    this.formIsDisabled = true;
    this.websocket.login(new AuthenticateWithPasswordRequest(param))
      .finally(() => {

        // Unclean
        this.ngOnInit();
        this.formIsDisabled = false;
      });
  }

  /**
  * Login to OpenEMS Edge or Backend for demo user.
  *
  * @param param data provided in login form
  */
  public doDemoLogin(param: { username?: string, password: string }) {

    this.websocket.login(new AuthenticateWithPasswordRequest(param)).then(() => {
      this.service.stopSpinner('loginspinner');
    });

    return new Promise<Edge[]>((resolve, reject) => {

      this.service.getEdges(this.page)
        .then((edges) => {
          setTimeout(() => {
            this.router.navigate(['/device', edges[0].id]);
          }, 100);
          resolve(edges);
        }).catch((err) => {
          reject(err);
        });
    }).finally(() => {
      this.service.stopSpinner('loginspinner');
    },
    );
  }

  ngOnDestroy() {
    this.stopOnDestroy.next();
    this.stopOnDestroy.complete();
  }
}
