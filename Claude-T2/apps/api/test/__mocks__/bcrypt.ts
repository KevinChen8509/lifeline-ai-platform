export const hash = jest.fn().mockResolvedValue('$2b$10$mockhashedpassword');
export const compare = jest.fn().mockResolvedValue(true);
export const genSalt = jest.fn().mockResolvedValue('$2b$10$mocksalt');
