import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MyActivity } from './my-activity';

describe('MyActivity', () => {
  let component: MyActivity;
  let fixture: ComponentFixture<MyActivity>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyActivity],
    }).compileComponents();

    fixture = TestBed.createComponent(MyActivity);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
