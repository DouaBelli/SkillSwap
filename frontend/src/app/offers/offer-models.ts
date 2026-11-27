/** Same shape as OfferDtos.OfferView in the backend. */
export interface OfferView {
  offerId: number;
  title: string;
  description: string;
  categoryId: number;
  categoryName: string;
  volunteerId: number;
  volunteerName: string;
  yearOfStudy: number;
  departmentName: string;
  availability: string;
  helpMethod: string;
  status: 'ACTIVE' | 'HIDDEN' | 'HIDDEN_BY_BLOCK';
  createdAt: string;
  mine: boolean;
}

/** Same shape as OfferDtos.OfferBody. */
export interface OfferBody {
  title: string;
  categoryId: number;
  description: string;
  availability: string;
  helpMethod: string;
}

/** Same shape as OfferDtos.OfferResult. */
export interface OfferResult {
  message: string;
  offer: OfferView;
}

export interface OfferFilters {
  categoryId: number | null;
  departmentId: number | null;
  keyword: string;
}
