import styled from 'styled-components'

export const PeopleGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
  gap: 16px;
`

export const SuperpowerRow = styled.ul`
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  li {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    overflow: hidden;
    display: grid;
    place-items: center;
    background: #e9e4f2;
    font-size: 12px;
    cursor: default;
  }
  img {
    width: 100%;
    height: 100%;
    margin: 0;
    object-fit: cover;
    display: block;
  }
`
