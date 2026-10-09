/**
 * Field definitions per role, driving a single generic profile form. Each field
 * maps 1:1 to a backend DTO property (see docs/03-DATABASE.md). Keeping this as
 * data (not JSX) means one form component renders every role's profile.
 */
const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

export const PROFILE_SCHEMA = {
  PATIENT: {
    resource: 'patients',
    fields: [
      { name: 'bloodGroup', label: 'Blood group', type: 'select', options: BLOOD_GROUPS },
      { name: 'age', label: 'Age', type: 'number' },
      { name: 'gender', label: 'Gender', type: 'select', options: [
        { value: 'MALE', label: 'Male' }, { value: 'FEMALE', label: 'Female' }, { value: 'OTHER', label: 'Other' },
      ] },
      { name: 'address', label: 'Address', type: 'text' },
      { name: 'emergencyContact', label: 'Emergency contact', type: 'text' },
    ],
  },
  // DONOR has a dedicated page: features/profile/donor/DonorProfilePage.jsx
  // HOSPITAL has a dedicated page: features/profile/hospital/HospitalProfilePage.jsx
  BLOOD_BANK: {
    resource: 'blood-banks',
    fields: [
      { name: 'bankName', label: 'Bank name', type: 'text', required: true },
      { name: 'licenseNumber', label: 'License number', type: 'text', required: true },
      { name: 'phone', label: 'Phone', type: 'text' },
      { name: 'address', label: 'Address', type: 'text' },
      { name: 'city', label: 'City', type: 'text' },
      { name: 'latitude', label: 'Latitude', type: 'number', geo: true },
      { name: 'longitude', label: 'Longitude', type: 'number', geo: true },
    ],
  },
};
