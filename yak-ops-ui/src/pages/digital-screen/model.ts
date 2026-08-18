export type DigitalScreenStatus = 'draft' | 'published';

export interface DigitalScreenInstance {
  id: string;
  name: string;
  description?: string;
  templateId: string;
  templateVersion: 1;
  status: DigitalScreenStatus;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface CreateDigitalScreenInput {
  name: string;
  description?: string;
  templateId: string;
}

export interface UpdateDigitalScreenInput {
  name?: string;
  description?: string;
}
