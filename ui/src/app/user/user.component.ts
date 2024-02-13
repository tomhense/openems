import { Component, OnInit } from '@angular/core';
import { FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Changelog } from 'src/app/changelog/view/component/changelog.constants';

import { environment } from '../../environments';
import { GetUserInformationRequest } from '../shared/jsonrpc/request/getUserInformationRequest';
import { SetUserInformationRequest } from '../shared/jsonrpc/request/setUserInformationRequest';
import { UpdateUserLanguageRequest } from '../shared/jsonrpc/request/updateUserLanguageRequest';
import { GetUserInformationResponse } from '../shared/jsonrpc/response/getUserInformationResponse';
import { Service, Websocket } from '../shared/shared';
import { COUNTRY_OPTIONS } from '../shared/type/country';
import { Language } from '../shared/type/language';
import { FormlyFieldConfig } from '@ngx-formly/core';

type UserInformation = {
  firstname: string,
  lastname: string,
  companyname?: string,
  email: string,
  phone: string,
  street: string,
  zip: string,
  city: string,
  country: string
}
@Component({
  templateUrl: './user.component.html',
})
export class UserComponent implements OnInit {

  public environment = environment;
  public uiVersion = Changelog.UI_VERSION;

  public readonly languages: Language[] = Language.ALL;
  public currentLanguage: Language;
  public isEditModeDisabled: boolean = true;
  public form: { formGroup: FormGroup, model: UserInformation };
  public showInformation: boolean = false;

  constructor(
    public translate: TranslateService,
    public service: Service,
    private route: ActivatedRoute,
    private websocket: Websocket,
  ) { }

  ngOnInit() {
    // Set currentLanguage to
    this.currentLanguage = Language.getByKey(localStorage.LANGUAGE) ?? Language.DEFAULT;
    this.service.setCurrentComponent({ languageKey: 'Menu.user' }, this.route);
    this.getUserInformation().then((userInformation) => {
      this.form = {
        formGroup: new FormGroup({}),
        model: userInformation,
      };
      this.showInformation = true;
    });
  }

  public applyChanges() {
    let user = {
      user: {
        lastname: this.form.model.lastname,
        firstname: this.form.model.firstname,
        email: this.form.model.email,
        phone: this.form.model.phone,
        address: {
          street: this.form.model.street,
          zip: this.form.model.zip,
          city: this.form.model.city,
          country: this.form.model.country,
        },
      },
    };
    this.service.websocket.sendRequest(new SetUserInformationRequest(user)).then(() => {
      this.service.toast(this.translate.instant('General.changeAccepted'), 'success');
    }).catch((reason) => {
      this.service.toast(this.translate.instant('General.changeFailed') + '\n' + reason.error.message, 'danger');
    });
    this.enableAndDisableFormFields();
    this.form.formGroup.markAsPristine();
  }

  public enableAndDisableEditMode(): void {
    if (this.isEditModeDisabled == false) {
      this.getUserInformation().then((userInformation) => {
        this.form = {
          formGroup: new FormGroup({}),
          model: userInformation,
        };
      });
    }

    this.enableAndDisableFormFields();
  }

  public enableAndDisableFormFields(): boolean {

    /** Fields, that are allowed to be edited for company assigned users */
    const ALLOWED_FIELDS_FOR_COMPANY_USER: string[] = [
      'firstname', 'lastname',
    ];

    const hasUserCompany = this.userInformationFields.find(field => field.key === 'companyname').model.companyname;

    // Update Fields
    this.userInformationFields.reduce((arr, field) => {

      if (hasUserCompany && ALLOWED_FIELDS_FOR_COMPANY_USER.find(key => key === field.key)?.length > 0) {
        field.props.disabled = !field.props.disabled;
      }

      if (!hasUserCompany) {
        field.props.disabled = !field.props.disabled;
      }

      arr.push(field);
      return arr;
    }, []);

    return this.isEditModeDisabled = !this.isEditModeDisabled;
  }

  /** Needs to be predefined to make wrapper work with ion-skeleton */
  protected userInformationFields: FormlyFieldConfig[] = [{
    key: "firstname",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.firstname"),
      disabled: true,
    },
  },
  {
    key: "lastname",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.lastname"),
      disabled: true,
    },
  },
  {
    key: "street",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.street"),
      disabled: true,
    },
  },
  {
    key: "zip",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.zip"),
      disabled: true,
    },
  },
  {
    key: "city",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.city"),
      disabled: true,
    },
  },
  {
    key: "country",
    type: "select",
    templateOptions: {
      label: this.translate.instant("Register.Form.country"),
      options: COUNTRY_OPTIONS(this.translate),
      disabled: true,
    },
  },
  {
    key: "email",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.email"),
      disabled: true,
    },
    validators: {
      validation: [Validators.email],
    },
  },
  {
    key: "phone",
    type: "input",
    templateOptions: {
      label: this.translate.instant("Register.Form.phone"),
      disabled: true,
    },
  },
  {
    key: "companyname",
    type: "input",
    props: {
      label: this.translate.instant('Register.Form.companyName'),
      disabled: true,
    },
    expressions: {
      hide: (fields) => !fields.model.companyname,
    },
  },
  ];

  public getUserInformation(): Promise<UserInformation> {

    return new Promise(resolve => {
      var interval = setInterval(() => {
        if (this.websocket.status == 'online') {
          this.service.websocket.sendRequest(new GetUserInformationRequest()).then((response: GetUserInformationResponse) => {
            let user = response.result.user;

            resolve({
              lastname: user.lastname,
              firstname: user.firstname,

              // Show company if available
              ...(user.company.name && { companyname: user.company.name }),
              email: user.email,
              phone: user.phone,
              street: user.address.street,
              zip: user.address.zip,
              city: user.address.city,
              country: user.address.country,
            });
          }).catch(() => {
            resolve({
              lastname: "",
              firstname: "",
              email: "",
              phone: "",
              street: "",
              zip: "",
              city: "",
              country: "",
            });
          });
          clearInterval(interval);
        }
      }, 1000);
    });
  }

  /**
   * Logout from OpenEMS Edge or Backend.
   */
  public doLogout() {
    this.websocket.logout();
  }

  public toggleDebugMode(event: CustomEvent) {
    localStorage.setItem("DEBUGMODE", event.detail['checked']);
    this.environment.debugMode = event.detail['checked'];
  }

  public setLanguage(language: Language): void {
    // Get Key of LanguageTag Enum
    localStorage.LANGUAGE = language.key;

    this.service.setLang(language);
    this.websocket.sendRequest(new UpdateUserLanguageRequest({ language: language.key })).then(() => {
      this.service.toast(this.translate.instant('General.changeAccepted'), 'success');
    }).catch((reason) => {
      this.service.toast(this.translate.instant('General.changeFailed') + '\n' + reason.error.message, 'danger');
    });

    this.currentLanguage = language;
    this.translate.use(language.key);
  }
}
