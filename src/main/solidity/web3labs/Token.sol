pragma solidity ^0.7.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/ERC20Burnable.sol";

contract Token is ERC20, ERC20Burnable {

    constructor(
        uint totalSupply,
        string memory name,
        string memory symbol,
        uint8 decimals) ERC20(name, symbol) {
        _setupDecimals(decimals);
        _mint(msg.sender, totalSupply);
    }
}
