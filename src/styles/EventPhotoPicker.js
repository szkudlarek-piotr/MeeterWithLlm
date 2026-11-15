import styled from 'styled-components'

export const PhotoPicker = styled.section`
  margin: 24px 0;
  padding-top: 20px;
  border-top: 1px solid var(--border);
`

export const PhotoPreview = styled.img`
  display: block;
  width: 100%;
  max-height: 360px;
  object-fit: contain;
  border-radius: 12px;
  margin: 14px 0;
  background: var(--soft);
`
