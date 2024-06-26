import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';

import { CustomViewsComponent } from './custom-views.component';

describe('CustomViewsComponent', () => {
  let component: CustomViewsComponent;
  let fixture: ComponentFixture<CustomViewsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, CustomViewsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomViewsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
