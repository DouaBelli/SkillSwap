import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RequestHelp } from './request-help';

describe('RequestHelp', () => {
  let component: RequestHelp;
  let fixture: ComponentFixture<RequestHelp>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RequestHelp],
    }).compileComponents();

    fixture = TestBed.createComponent(RequestHelp);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
